import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;

public class parser {

    public static void main(String[] args) throws Exception, IOException {

        System.setProperty("webdriver.gecko.driver", "C:\\Users\\timoc\\Desktop\\project_data\\geckodriver.exe");
        WebDriver driver = new FirefoxDriver();
        Document doc;
        comment c;
        submission s;
        File f = new File(args[0]);
        String commentOrSubmission = args[0];
        System.out.println(args[0]);
        String path;

        // RS => Submissions  RC=> Comments
        if (commentOrSubmission.contains("RC")) {

            path = deleteEnding(commentOrSubmission) + ".tsv";
            List<photoshop_out> outList = new ArrayList<photoshop_out>();
            List<String> unsupportedUrl=new ArrayList<>();

            // Read json file to list
            ObjectMapper mapper = new ObjectMapper();
            comment[] commentList = mapper.readValue(f, comment[].class);

            for (comment comment : commentList) {
                c = comment;

                // first check if it is a top level comment by looking at the prefix of parent_id="t3"_id
                // also look at the score to get rid of spam => score >= 20
                String parent_id = c.getParent_id();
                String prefix = parent_id.substring(0, 2);
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
                    if (links.size() < 1) { // happens if comments are deleted
                       // System.out.println("no url found for :  " + c.getId());
                        continue;
                    }

                    //System.out.println(links.get(0));

                    int imgCounterLink = 0;

                    boolean processedLink=false; //check if a link provides at least one image if not write to unsupported file

                    for (String link : links) {


                        processedLink=false;
                        //create permalink referring to the post
                        String linkBuilder = c.getPermalink();
                        if (linkBuilder == null) {
                            linkBuilder = "reddit.com/r/photoshopbattles/comments/" + c.getParent_id().substring(3);
                        } else {
                            linkBuilder = "reddit.com/" + c.getPermalink();

                            //  /r/photoshopbattles/comments/2nw093/
                        }
                        //check if link points directly to an img
                        if (link.endsWith("png") || link.endsWith("jpg") || link.endsWith("jpeg")) {
                            URL url = new URL(link);
                            BufferedImage bimg = ImageIO.read(url);

                            //it might be https instead of http
                            if (bimg == null) {
                                String https = "https";
                                String nUrl = "https" + link.substring(4);
                                url = new URL(nUrl);
                                bimg = ImageIO.read(url);
                                imgCounterLink++;
                            }
                            if (bimg == null) {
                                System.out.println("fail");
                                continue;
                            }

                            WritableRaster raster = bimg.getRaster();
                            DataBufferByte data = (DataBufferByte) raster.getDataBuffer();
                            byte[] imgByte = data.getData();
                            String idSuffix = String.valueOf(imgCounterLink);

                            if (imgByte.length < 10000) {  //check for min size of 10kB for and image
                                continue;
                            }

                            photoshop_out po = new photoshop_out(c.getId() + "_" + idSuffix, parent_id.substring(3), links.get(0), getFormat(link), sha256Hex(imgByte), String.valueOf(imgByte.length), c.getScore(), c.getAuthor(), linkBuilder, c.getCreated_utc(), bimg.getWidth(), bimg.getHeight());
                            outList.add(po);
                            processedLink=true;

                        } else { // url does not refer directly to and image (might be multiple images)
                            // link = "http://imgur.com/gallery/GBEm11n";

                            driver.get(link);
                            Thread.sleep(2000); // easiest way to wait till any page is loaded

                            doc = Jsoup.parse(driver.getPageSource());

                            Elements img = doc.getElementsByTag("img");
                            String imgDuplicate = "";
                            //System.out.println(img.size()+ "  number of img elements found");
                            for (Element element : img) {
                                String src = element.absUrl("src");
                                if (src.toLowerCase().contains("i.imgur")) { //
                                    if (src.endsWith("png") || src.endsWith("jpg") || src.endsWith("jpeg")) {
                                        //byte[] image = getConnection(src).execute().bodyAsBytes();
                                        //System.out.println(image.length + "   img length   " + src);

                                        //check for img duplicates, since images on e.g. imgur are present multiple times with different endings
                                        if (imgDuplicate.equals(deleteEnding(src))) {
                                            continue;
                                        }

                                        imgDuplicate = deleteEnding(src);
                                        URL url = new URL(src);
                                        BufferedImage bimg = ImageIO.read(url);

                                        WritableRaster raster = bimg.getRaster();
                                        DataBufferByte data = (DataBufferByte) raster.getDataBuffer();
                                        byte[] imgByte = data.getData();
                                        String idSuffix = String.valueOf(imgCounterLink);

                                        if (imgByte.length < 10000) { //check for min size of 10kB for and image
                                            continue;
                                        }

                                        //create photoshop out which is the content of the output file
                                        photoshop_out po = new photoshop_out(c.getId() + "_" + idSuffix, parent_id.substring(3), src, getFormat(src), sha256Hex(imgByte), String.valueOf(imgByte.length), c.getScore(), c.getAuthor(), linkBuilder, c.getCreated_utc(), bimg.getWidth(), bimg.getHeight());
                                        outList.add(po);
                                        processedLink=true;


                                    }
                                }

                            }


                        }
                        if(!processedLink){
                            unsupportedUrl.add(link);

                        }

                    }


                }


            }
            driver.close();

            CsvMapper mapperCSV = new CsvMapper();
            mapperCSV.disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
            CsvSchema schema = mapperCSV.schemaFor(photoshop_out.class).withHeader();
            schema = schema.withColumnSeparator('\t');
            ObjectWriter myObjectWriter = mapperCSV.writer(schema);
            myObjectWriter.writeValue(Paths.get(path).toFile(), outList);

            //write file with unsupported urls
            FileWriter writer= new FileWriter(deleteEnding(commentOrSubmission)+"_unsupported.txt");
            for (String str: unsupportedUrl){
                writer.write(str+System.lineSeparator());
            }
            writer.close();

        }


    }

    public static String getFormat(String url) {
        String[] split = url.split("\\.");
        return split[split.length - 1];
    }

    public static Connection getConnection(String url) {
        return Jsoup.connect(url).followRedirects(true).timeout(10_000).maxBodySize(40_000_000).ignoreContentType(true);
    }

    public static String deleteEnding(String withEnding) {
        String[] split = withEnding.split("\\.");
        int cutSize = withEnding.length() - split[split.length - 1].length();
        return withEnding.substring(0, cutSize - 1);

    }

}

