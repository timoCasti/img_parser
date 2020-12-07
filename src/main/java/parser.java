import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;

public class parser {

    public static void main(String[] args) throws Exception {

        String geckodriverPath = args[1];
        System.setProperty("webdriver.gecko.driver", geckodriverPath);
        FirefoxOptions options = new FirefoxOptions();
        options.setHeadless(true);

        File f = new File(args[0]);
        String commentOrSubmission = args[0];
        System.out.println(args[0]);


        WebDriver driver = new FirefoxDriver(options);
        // RS => Submissions  RC=> Comments

        try {
            if (commentOrSubmission.contains("RC")) {

                parseComments(f, commentOrSubmission, driver);

            } else if (commentOrSubmission.contains("RS")) {

                parseSubmissions(f, commentOrSubmission, driver);

            } else {
                System.out.println("Not Desired File Name");

            }

        } finally {

            driver.quit();
        }


    }

    static void testmethod(List<String> l, WebDriver driver) throws Exception {
        for (String s : l) {
            //driver.get("http://i.imgur.com/i6zrfVR.jpg");
            // URL u=new URL(l);

            try {
                //  System.out.println(" sutl 323  :  "+sUrl);
                driver.get(s);
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("l. 319  driver.get() failed for url:  ");

            }
            Thread.sleep(1500); // easiest way to wait till any page is loaded


            Document doc = null;
            try {
                doc = Jsoup.parse(driver.getPageSource());

            } catch (Exception e) {
                e.printStackTrace();

            }
            if (doc != null) {
                System.out.println(doc.title());
            }
        }

    }

    private static String getFormat(String url) {
        String[] split = url.split("\\.");
        return split[split.length - 1];
    }

    public static Connection getConnection(String url) {
        return Jsoup.connect(url).followRedirects(true).timeout(10_000).maxBodySize(40_000_000).ignoreContentType(true);
    }

    private static String deleteEnding(String withEnding) {
        String[] split = withEnding.split("\\.");
        int cutSize = withEnding.length() - split[split.length - 1].length();
        return withEnding.substring(0, cutSize - 1);

    }

    private static ArrayList<String> getUrlsFromBody(String body) {
        ArrayList<String> links = new ArrayList<>();
        String regex = "\\(?\\b(http://|www[.])[-A-Za-z0-9+&@#/%?=~_()|!:,.;]*[-A-Za-z0-9+&@#/%=~_()|]";//"^((https?|ftp|smtp):\\/\\/)?(www.)?[a-z0-9]+\\.[a-z]+(\\/[a-zA-Z0-9#]+\\/?)*$"; //regex from:https://stackoverflow.com/a/42619368 fails for some reason
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(body);
        while (m.find()) {
            String urlStr = m.group();
            while (urlStr.startsWith("(")) {
                urlStr = urlStr.substring(1);
            }
            while (urlStr.endsWith(")")) {
                urlStr = urlStr.substring(0, urlStr.length() - 1);
            }

            links.add(urlStr);
        }
        return links;
    }

    // Writes parsed file to tsv and writes a file with unsupported url's
    private static <T> void finalizeOutput(List<T> outputlist, String path, List<String> unsupportedUrl) throws IOException {

        //write outputlist to .tsv file
        CsvMapper mapperCSV = new CsvMapper();
        mapperCSV.disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);

        if (outputlist.size() == 0) {
            return;
        }

        if (outputlist.get(0) instanceof photoshop_out) {
            CsvSchema schema = mapperCSV.schemaFor(photoshop_out.class).withHeader();
            schema = schema.withColumnSeparator('\t').withoutQuoteChar();
            ObjectWriter myObjectWriter = mapperCSV.writer(schema);
            myObjectWriter.writeValue(Paths.get(path).toFile(), outputlist);
        } else {
            CsvSchema schema = mapperCSV.schemaFor(original_out.class).withHeader();
            schema = schema.withColumnSeparator('\t').withoutQuoteChar();
            ObjectWriter myObjectWriter = mapperCSV.writer(schema);
            myObjectWriter.writeValue(Paths.get(path).toFile(), outputlist);
        }

        //write file with unsupported urls
        FileWriter writer = new FileWriter(deleteEnding(path) + "_unsupported.txt");
        for (String str : unsupportedUrl) {
            writer.write(str + System.lineSeparator());
        }
        writer.close();
    }

    private static void parseComments(File file, String commentOrSubmission, WebDriver driver) throws IOException {

        // WebDriver driverC=new FirefoxDriver(options);

        String path = deleteEnding(commentOrSubmission) + ".tsv";
        List<photoshop_out> outList = new ArrayList<>();
        List<String> unsupportedUrl = new ArrayList<>();
        int counter_toplvl = 0;
        int counter_score = 0;
        int counter_imgSize = 0;
        int counter_multiplImages = 0;
        int counter_comments = 0;
        int counter_success = 0;

        // Read json file to list
        ObjectMapper mapper = new ObjectMapper();
        comment[] commentList = mapper.readValue(file, comment[].class);

        for (comment comment : commentList) {

            counter_comments++;
            // first check if it is a top level comment by looking at the prefix of parent_id="t3"_id
            // also look at the score to get rid of spam => score >= 20
            String parent_id = comment.getParent_id();
            String prefix = parent_id.substring(0, 2);
            int sc = Integer.parseInt(comment.getScore());

            if (!prefix.equals("t3")) {
                counter_toplvl++;
                continue;
            }
            if (sc < 20) {
                counter_score++;
                continue;
            }

            //top lvl comment, now find link of image in the body
            String bo = comment.getBody();
            ArrayList<String> links = getUrlsFromBody(bo);

            if (!(links.size() == 1)) { // we only consider comments with only one link
                counter_multiplImages++;
                continue;
            }
            BufferedImage bimg = null;
            int imgCounterLink = 0;

            boolean processedLink = false; //check if a link provides at least one image if not write to unsupported file

            String link = links.get(0);
            String linkBuilder = "reddit.com/r/photoshopbattles/comments/" + comment.getParent_id().substring(3) + "/a/" + comment.getId();

            try {
                driver.get(link);
                Thread.sleep(1500); // easiest way to wait till any page is loaded
            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }


            Document doc = Jsoup.parse(driver.getPageSource());

            Elements img = doc.getElementsByTag("img");
            String imgDuplicate = "";
            //System.out.println(img.size()+ "  number of img elements found");
            for (Element element : img) {
                String src = element.absUrl("src");
                if (src.toLowerCase().contains("i.imgur") || src.toLowerCase().contains("pinimg.com") || src.toLowerCase().contains("pbs.twigmg.com") || src.toLowerCase().contains("upload.wikimedia.org") || src.toLowerCase().contains("ytimg.com") || src.toLowerCase().contains("i.reddituploads.com") || src.toLowerCase().contains("puu.sh") || src.toLowerCase().contains("flickr.com") || src.toLowerCase().contains("deviantart.com") || src.toLowerCase().contains("en.wikipedia.org") || src.toLowerCase().contains("i.redd.it")) { //

                    //      if (getFormat(src).contains("png") || getFormat(src).contains("jpg") || getFormat(src).contains("jpeg")) {

                    //check for img duplicates, since images on e.g. imgur are present multiple times with different endings
                    if (imgDuplicate.equals(deleteEnding(src))) {
                        continue;
                    }
                    imgDuplicate = deleteEnding(src);

                    URL url = null;
                    try {
                        url = new URL(src);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (url == null) {
                        continue;
                    }

                    try {
                        bimg = ImageIO.read(url);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (bimg == null) {
                        continue;
                    }

                    byte[] imgByte = null;
                    try {
                        imgByte = getConnection(src).execute().bodyAsBytes();
                    } catch (Exception e) {
                        e.printStackTrace();
                        continue;
                    }
                    String format = "";
                    try {
                        format = URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(imgByte));
                        if (format.startsWith("image/")) {
                            format = format.substring(6);
                        }
                    } catch (Exception e) {
                        format = getFormat(src);
                        e.printStackTrace();
                    }


                    String idSuffix = String.valueOf(imgCounterLink);

                    if (imgByte.length < 10000 || bimg.getWidth() < 300 || bimg.getHeight() < 300) { //check for min size of 10kB for and image and is at least 300x300
                        counter_imgSize++;
                        continue;
                    }

                    //trim img url remove everything after question mark
                    src = trimUrl(src);

                    //create photoshop out which is the content of the output file
                    photoshop_out po = new photoshop_out(comment.getId() + "_" + idSuffix, parent_id.substring(3), src, format, sha256Hex(imgByte), String.valueOf(imgByte.length), comment.getScore(), comment.getAuthor(), linkBuilder, comment.getCreated_utc(), bimg.getWidth(), bimg.getHeight());
                    outList.add(po);
                    processedLink = true;
                    counter_success++;
                    //}
                }

            }
            if (!processedLink) { //saves links which do not provide an image to an additional file
                unsupportedUrl.add(link);

            }


        }
        unsupportedUrl.add(0, "Number of comments successful parsed:  " + counter_success);
        unsupportedUrl.add(1, "Number of comments parsed:  " + counter_comments);
        unsupportedUrl.add(2, "Number of comments which are not top lvl:   " + counter_toplvl);
        unsupportedUrl.add(3, "Number of comments with score below 20  :  " + counter_score);
        unsupportedUrl.add(4, "Number of comments with more than one image:  " + counter_multiplImages);
        unsupportedUrl.add(5, "Number of comments with too small images:  " + counter_imgSize);
        finalizeOutput(outList, path, unsupportedUrl);
    }

    private static void parseSubmissions(File f, String commentOrSubmission, WebDriver driver) throws Exception {
        //submissions file

        String path = deleteEnding(commentOrSubmission) + ".tsv";
        List<original_out> outList = new ArrayList<>();
        List<String> unsupportedUrl = new ArrayList<>();

        int counter_score = 0;
        int counter_imgSize = 0;
        int counter_submisssions = 0;
        int counter_success = 0;
        // Read json file to list
        ObjectMapper mapper = new ObjectMapper();
        submission[] submissionList = mapper.readValue(f, submission[].class);
        int emptyURL = 0;

        BufferedImage bimg = null;
        for (submission sub : submissionList) {
            counter_submisssions++;
            boolean processedSubmission = false;

            //create permalink referring to the post
            String linkBuilder = "reddit.com/r/photoshopbattles/comments/" + sub.getId();

            String sUrl = sub.getUrl();
            int sc = Integer.parseInt(sub.getScore());

            if (sc < 20) {  //score of post needs to be at least 20, to filter spam
                counter_score++;
                continue;
            }
            String ending = getFormat(sUrl);

            boolean isImg = ending.contains("png") || ending.contains("jpg") || ending.contains("jpeg");
            if (isImg) {

                URL url = null;
                try {
                    url = new URL(sUrl);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (url == null) {
                    System.out.println("no url");
                    continue;
                }
                try {
                    bimg = ImageIO.read(url);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                //it might be https instead of http
                if (bimg == null && sUrl.startsWith("http:")) {
                    String nUrl = "https" + sUrl.substring(4);
                    url = new URL(nUrl);
                    try {
                        bimg = ImageIO.read(url);

                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
                if (bimg == null) {
                    System.out.println("failed to open url:  " + sUrl);
                    continue;
                }

                byte[] imgByte = null;
                try {
                    imgByte = getConnection(sUrl).execute().bodyAsBytes();
                } catch (Exception e) {
                    e.printStackTrace();
                    continue;
                }
                String format = "";
                try {
                    format = URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(imgByte));
                    if (format.startsWith("image/")) {
                        format = format.substring(6);
                    }

                } catch (Exception e) {
                    format = getFormat(sUrl);
                    e.printStackTrace();
                }
                if (imgByte.length < 10000 || bimg.getHeight() < 300 || bimg.getWidth() < 300) {  //check for min size of 10kB for and image
                    counter_imgSize++;
                    continue;
                }

                original_out po = new original_out(sub.getId(), sub.getUrl(), format, sha256Hex(imgByte), String.valueOf(imgByte.length), sub.getScore(), sub.getAuthor(), linkBuilder, sub.getCreated_utc(), bimg.getWidth(), bimg.getHeight());
                outList.add(po);
                processedSubmission = true;
                counter_success++;


            } //url does not directly refer to an image checkout provided url for images with headless Browser
            else if (!sUrl.equals("")) {

                //try {
                try {
                    driver.get(sUrl);
                } catch (Exception e) {
                    e.printStackTrace();
                    continue;
                }
                Thread.sleep(1500); // easiest way to wait till any page is loaded

                Document doc;
                try {
                    doc = Jsoup.parse(driver.getPageSource());

                } catch (Exception e) {
                    e.printStackTrace();
                    continue;
                }
                if (doc == null) {
                    continue;
                }
                Elements img = doc.getElementsByTag("img");

                String imgDuplicate = "";
                for (Element element : img) {
                    String src = element.absUrl("src");
                    if (src.toLowerCase().contains("i.imgur") || src.toLowerCase().contains("pinimg.com") || src.toLowerCase().contains("pbs.twigmg.com") || src.toLowerCase().contains("upload.wikimedia.org") || src.toLowerCase().contains("ytimg.com") || src.toLowerCase().contains("i.reddituploads.com") || src.toLowerCase().contains("puu.sh") || src.toLowerCase().contains("flickr.com") || src.toLowerCase().contains("deviantart.com") || src.toLowerCase().contains("en.wikipedia.org") || src.toLowerCase().contains("i.redd.it")) { //

                        //check for img duplicates, since images on e.g. imgur are present multiple times with different endings
                        if (imgDuplicate.equals(deleteEnding(src))) {
                            continue;
                        }
                        imgDuplicate = deleteEnding(src);
                        URL url;
                        try {
                            url = new URL(src);
                        } catch (Exception e) {
                            e.printStackTrace();
                            continue;
                        }
                        try {
                            bimg = ImageIO.read(url);
                        } catch (IOException e) {
                            e.printStackTrace();
                            continue;
                        }
                        if (bimg == null) {
                            continue;
                        }
                        byte[] imgByte = null;
                        try {
                            imgByte = getConnection(sUrl).execute().bodyAsBytes();
                        } catch (Exception e) {
                            e.printStackTrace();
                            continue;
                        }
                        String format = "";
                        try {
                            format = URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(imgByte));
                            if (format.startsWith("image/")) {
                                format = format.substring(6);
                            }
                        } catch (Exception e) {
                            format = getFormat(src);
                            e.printStackTrace();
                        }

                        if (imgByte.length < 10000 || bimg.getHeight() < 300 || bimg.getWidth() < 300) { //check for min size of 10kB for and image
                            counter_imgSize++;
                            continue;
                        }
                        //trim img url remove everything after question mark
                        src = trimUrl(src);

                        //create original_out which holds the content of the output file
                        original_out po = new original_out(sub.getId(), src, format, sha256Hex(imgByte), String.valueOf(imgByte.length), sub.getScore(), sub.getAuthor(), linkBuilder, sub.getCreated_utc(), bimg.getWidth(), bimg.getHeight());
                        outList.add(po);
                        processedSubmission = true;
                        counter_success++;
                    }
                }

            } else {//url is empty
                emptyURL++;
            }
            if (!processedSubmission) {
                unsupportedUrl.add(sUrl);
            }
        }
        unsupportedUrl.add("Number of empty url:  " + emptyURL);
        unsupportedUrl.add(0, "Number of submissions successful parsed:  " + counter_success);
        unsupportedUrl.add(1, "Number of submissions parsed:  " + counter_submisssions);
        unsupportedUrl.add(2, "Number of submissions with score below 20:  " + counter_score);
        unsupportedUrl.add(3, "Number of submissions with too small images:  " + counter_imgSize);

        finalizeOutput(outList, path, unsupportedUrl);

    }

    //trim img url remove everything after question mark
    private static String trimUrl(String url) {

        int index = url.indexOf('?');
        if (index != -1) {
            url = url.substring(0, index);
        }
        return url;

    }


    // not used, would be an attempt to retrieve Submission title for permalink
    public static String getTitleOfPost(String link, String commentId) throws InterruptedException {

        String linkBuilder = link;
        FirefoxOptions options = new FirefoxOptions();
        options.setHeadless(true);
        WebDriver driver1 = new FirefoxDriver(options);
        Document doc1;
        try {
            driver1.get(linkBuilder);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Thread.sleep(1500); // easiest way to wait till any page is loaded
        doc1 = Jsoup.parse(driver1.getPageSource());

        String title = doc1.title();

        //trim img url remove everything after question mark
        int index = title.indexOf(':');
        if (index != -1) {
            title = title.substring(index + 1);
        }
        index = title.indexOf(':');
        if (index != -1) {
            title = title.substring(0, index);
        }
        if (title.length() > 0) {
            while (title.startsWith(" ")) {
                title = title.substring(1);
            }
            while (title.endsWith(" ")) {
                title = title.substring(0, title.length() - 2);
            }
        }
        title = title.replaceAll(" ", "_").toLowerCase();

        linkBuilder = linkBuilder + "/" + title + "/" + commentId;

        driver1.quit();
        return linkBuilder;
    }


}

