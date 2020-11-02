import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.apache.commons.io.FilenameUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;

public class parser {

    public static void main(String[] args) throws Exception, IOException {


        comment c;
        submission s;
        File f = new File(args[0]);
        String commentOrSubmission = args[0];
        System.out.println(args[0]);
        int le=commentOrSubmission.length();
        //String cut=commentOrSubmission.substring()
        String path;
        // RS => Submissions  RC=> Comments
        if (commentOrSubmission.contains("RC")) {

            path=commentOrSubmission+"ps.tsv";
            System.out.println(path);
            //MappingIterator<comment> iterator;
            //iterator = new ObjectMapper().readerFor(comment.class).without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValues(f);
            List<photoshop_out> outList = new ArrayList<photoshop_out>();
            //System.out.println(f.toString());

            //while (iterator.hasNextValue()) {
            //    c = iterator.nextValue();


            // Read json file to list

            ObjectMapper mapper=new ObjectMapper();
            comment[] commentList = mapper.readValue(f,comment[].class);

            for (comment comment : commentList) {
                c = comment;

                // first check if it is a top level comment by looking at the prefix of parent_id="t3"_id
                // also look at the score to get rid of spam => score >= 20
                String str = c.getParent_id();
                String prefix = str.substring(0, 2);
                int sc = Integer.parseInt(c.getScore());


                if (!prefix.equals("t3") | sc < 20) {
                   // System.out.println("not top lvl comment or score too low");
                } else {
                    //top lvl comment, now find link of image in the body
                    String bo = c.getBody();
                    ArrayList<String> links = new ArrayList<>();
                    String regex = "\\(?\\b(http://|www[.])[-A-Za-z0-9+&@#/%?=~_()|!:,.;]*[-A-Za-z0-9+&@#/%=~_()|]";
                    Pattern p = Pattern.compile(regex);
                    Matcher m = p.matcher(bo);
                    while (m.find()) {
                        String urlStr = m.group();
                        if (urlStr.startsWith("(") && urlStr.endsWith(")")) {
                            urlStr = urlStr.substring(1, urlStr.length() - 1);
                        }
                        links.add(urlStr);

                    }
                    if (links.size() < 1) {
                        System.out.println("no url found for :  "+ c.getId()); // happens if comments are deleted
                        continue;
                    }

                    //System.out.println(links.get(0));


                    for (String link:links){

                        photoshop_out out = new photoshop_out();
                        //create permalink
                        String linkBuilder=c.getPermalink();
                        if(linkBuilder==null){
                            linkBuilder="reddit.com/r/photoshopbattles/comments/"+c.getParent_id().substring(3);
                        }
                        else {
                            linkBuilder="reddit.com/"+c.getPermalink();

                            //  /r/photoshopbattles/comments/2nw093/
                        }
                        //check if link is an img
                        if(link.endsWith("png")||link.endsWith("jpg")||link.endsWith("jpeg")){

                            URL url=new URL(link);
                            BufferedImage bimg=ImageIO.read(url);
                           // System.out.println(link);
                           // String a="https://i.ytimg.com/vi/3Tw1OEmiebs/maxresdefault.jpg";
                           // URL url=new URL(a);
                           // Image img=ImageIO.read(url);
                           // BufferedImage bimg=(BufferedImage)img;//ImageIO.read(url);
                           // bimg=ImageIO.read(new URL(link).openStream());

                            //it might be https instead of http
                            if(bimg==null) {
                                String https = "https";
                                String nUrl = "https" + link.substring(4);
                                url = new URL(nUrl);
                                bimg = ImageIO.read(url);
                            }
                            if(bimg==null){

                                System.out.println("fail");
                                continue;
                            }

                            WritableRaster raster=bimg.getRaster();

                            DataBufferByte data = (DataBufferByte) raster.getDataBuffer();
                            byte[] imgByte=data.getData();

                            out.setId(c.getId());
                            out.setOriginal(str.substring(3));
                            out.setAuthor(c.getAuthor());
                            out.setEnd(FilenameUtils.getExtension(link)); // FilenameUtils.getExtension from Apache commons io
                            out.setHeight(bimg.getHeight());
                            out.setWidth(bimg.getWidth());
                            out.setFilesize(String.valueOf(imgByte.length));
                            out.setLink(linkBuilder); //check if permalink is null to construct it with id
                            out.setUrl(links.get(0));
                            out.setScore(c.getScore());
                            out.setTimestamp(c.getCreated_utc());
                            out.setHash(sha256Hex(imgByte));

                            outList.add(out);


                        }else { // url does not refer directly to and image

                            if(link.startsWith("http:")){
                                String https = "https";
                                link= "https" + link.substring(4);
                            }
                          //  link="https://imgur.com/jzN0d";


                            System.out.println("not an img link:  "+link);

                            Document doc = Jsoup.connect(link).maxBodySize(0).userAgent("Mozialla").get();
                            System.out.println(doc.body());

                           // System.out.println(doc.getAllElements().size());
                            Elements getel =doc.getElementsByTag("img");

                            System.out.println("getEl:  "+ getel.size());
                            System.out.println(getel.attr("src"));
                            Elements el=doc.select("img");
                           //System.out.println(el.size());

                            //Elements images=doc.getElementsByTag("img");
                            //Elements images=doc.select("img");
                            //System.out.println(images.size()+"  size of imgaes");
                           // Elements images =doc.select("img[src~=(?i)\\.(png|jpe?g)]");
                            Elements images =doc.select("img");

                            for (Element image:images){

                                System.out.println(image.attr("src"));
                                System.out.println(image.attr("alt"));
                                System.out.println(image.attr("height"));


                            }


                        }






                    }





                    //get the image
                    /*
                    BufferedImage img;
                    URL url = new URL(links.get(0));
                    ImageInputStream iis = ImageIO.createImageInputStream(url);
                    Iterator<ImageReader> imageReaders = ImageIO.getImageReaders(iis);
                    if (!imageReaders.hasNext()) {
                        System.out.println("no image");
                        continue;
                    }
                    ImageReader reader = imageReaders.next();
                    String ending = reader.getFormatName();
                    img = ImageIO.read(url);
                    File imgF = new File(links.get(0));

                    // find out the size of the image
*/


                    //preparing output

                   /* photoshop_out out = new photoshop_out();
                    out.setId(c.getId());
                    out.setOriginal(str.substring(3));
                    out.setAuthor(c.getAuthor());
                   // out.setEnd(ending); // FilenameUtils.getExtension from Apache commons io
                   // out.setHeight(img.getHeight());
                   // out.setWidth(img.getWidth());
                   // out.setFilesize(String.valueOf(imgF.length()));
                    out.setLink(linkBuilder); //check if permalink is null to construct it with id
                    out.setUrl(links.get(0));
                    out.setScore(c.getScore());
                    out.setTimestamp(c.getCreated_utc());

                    outList.add(out);
*/

                }


            }
            CsvMapper mapperCSV = new CsvMapper();
            CsvSchema schema= mapperCSV.schemaFor(photoshop_out.class).withHeader();
            schema=schema.withColumnSeparator('\t');
            ObjectWriter myObjectWriter=mapperCSV.writer(schema);
            myObjectWriter.writeValue(Paths.get(path).toFile(),outList);




            /*
            System.out.println(submissionList.size() + "    size list");
            String path = args[0] + "_psb.json";
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                objectMapper.writeValue(Paths.get(path).toFile(), submissionList);

            } catch (Exception e) {
                e.printStackTrace();
            }

            System.out.println("number of submission elements written:  " + submissionList.size());


        } else {
            MappingIterator<comment> iterator;
            iterator = new ObjectMapper().readerFor(comment.class).without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValues(f);
            List<comment> commentList = new ArrayList<comment>();
            System.out.println(f.toString());

            while (iterator.hasNextValue()) {
                c = iterator.nextValue();
                //check if subbreddit_id equals the subreddit id of PSbattle
                if (c.getSubreddit_id().equals("t5_2tecy")) {
                    // System.out.println(c.getSubreddit_id());
                    commentList.add(c);
                }


            }
            System.out.println(commentList.size() + "    size list");
            String path = args[0] + "_psb.json";

            int size = commentList.size();
            */

            //write to file




        /*for(int i=size;i>0;i--){

            if(!commentList.get(i-1).getSubreddit_id().equals("t5_2tecy")){
                commentList.remove(i-1);
            }
        }*/
            /*
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                // String json= objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(commentList);
                // System.out.println(json);
                objectMapper.writeValue(Paths.get(path).toFile(), commentList);

            } catch (Exception e) {
                e.printStackTrace();
            }
*/

            //comment c = objectMapper.readValue(f, comment.class);

            //List<comment> listComment = objectMapper.readValue(f, new TypeReference<List<comment>>(){});
            //System.out.println(commentList.get(7));
            //System.out.println("number of elements written:  " + commentList.size());
        }


    }


}

