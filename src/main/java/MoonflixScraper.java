import com.sun.istack.internal.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.FileWriter;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scrape https://www.moonflix.co.nz in to JSON
 */
public class MoonflixScraper {
    private static final String
            FILENAME = "content.json",
            TITLE_KEY = "title",
            URL_KEY = "url",
            VIDEOS_LIST_KEY = "videos",
            SHOWS_LIST_KEY = "shows",
            VIDEO_DOWNLOAD_KEY = "download_url";

    public static void main(String[] args) {
        JSONObject data = scrapeMoonflix();

        // Failed to scrape the website at all
        if(data == null) {
            System.out.println("Something went wrong, scraping failed!");
            return;
        }

        // Successfully scraped and written to file
        if(writeToFile(data.toString(), FILENAME)) {
            System.out.println("Result written to file: " + FILENAME);
        }

        // Successfully scraped but failed to write to the file
        else {
            System.out.println(
                    "Error writing the result to the file: " + FILENAME
                            + "\nResult:\n\n" + data.toString()
            );
        }
    }

    /**
     * Attempt to scrape the details of all shows on Moonflix.
     *
     * @return JSON containing show details
     */
    @Nullable
    private static JSONObject scrapeMoonflix() {

        // Landing page lists all content
        final String baseUrl = "https://www.moonflix.co.nz/";
        JSONArray showsArr = new JSONArray();

        try {
            Document moonflix = Jsoup.connect(baseUrl).get();

            // Each show is in its own container with a title and a videos container
            Elements shows = moonflix.getElementsByClass("video-section");

            for(Element show : shows) {

                // Holds a title container and videos container
                Element contentContainer = show.selectFirst(".container");

                // Not a show
                if(contentContainer == null) {
                    continue;
                }

                // Holds URL to show's page & show title
                Element titleContainer = contentContainer.selectFirst(".link-category");

                // Missing title container (may not be a show)
                if(titleContainer == null) {
                    continue;
                }

                Element titleElement = titleContainer.selectFirst(".title-section");
                Element videoContainer = contentContainer.selectFirst(".w-dyn-items");

                // No title/videos inside container (shouldn't happen)
                if(titleElement == null || videoContainer == null) {
                    continue;
                }

                String showTitle = titleElement.text();
                String showUrl = titleContainer.absUrl("href");
                Elements videos = videoContainer.children();

                System.out.println("Title: " + showTitle + "\nURL: " + showUrl + "\nVideos: " + videos.size() + "\n");

                JSONArray videosArr = new JSONArray();

                for(int i = 0; i < videos.size(); i++) {
                    System.out.println("Scraping video " + (i + 1) + "/" + videos.size() + "...");
                    JSONObject video = scrapeVideoDetails(videos.get(i));

                    // Failed to scrape video (might not be a video)
                    if(video == null) {
                        continue;
                    }

                    System.out.println(
                            "Title: " + video.getString(TITLE_KEY)
                                    + "\nURL: " + video.getString(URL_KEY)
                                    + "\nDownload URL: " + video.getString(VIDEO_DOWNLOAD_KEY)
                                    + "\n"
                    );
                    videosArr.put(video);
                }

                showsArr.put(
                        new JSONObject()
                                .put(TITLE_KEY, showTitle)
                                .put(URL_KEY, showUrl)
                                .put(VIDEOS_LIST_KEY, videosArr)
                );
            }

            System.out.println("Complete!\n");
            return new JSONObject().put(SHOWS_LIST_KEY, showsArr);
        }
        catch(Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Scrape the details of a Moonflix video from its HTML element.
     * This element is the child of a show element on the main page of Moonflix.
     *
     * @param videoElement HTML element representing video from Moonflix main page
     * @return JSON containing video details
     */
    @Nullable
    private static JSONObject scrapeVideoDetails(Element videoElement) {
        try {
            Element videoDetails = videoElement.selectFirst(".link-video");

            // No details for video
            if(videoDetails == null) {
                throw new Exception("No details found!");
            }

            Element videoTitleElement = videoDetails.selectFirst(".div-title-video");

            // No title for video
            if(videoTitleElement == null) {
                throw new Exception("No title for video found!");
            }

            String videoUrl = videoDetails.absUrl("href");
            String downloadUrl = fetchVideoDownloadUrl(videoUrl);

            // Wait and try once more
            if(downloadUrl == null) {
                System.out.println("\nError, sleeping for 10 seconds...\n");
                Thread.sleep(10000);
                downloadUrl = fetchVideoDownloadUrl(videoUrl);
            }

            return new JSONObject()
                    .put(TITLE_KEY, videoTitleElement.text())
                    .put(URL_KEY, videoUrl)
                    .put(VIDEO_DOWNLOAD_KEY, downloadUrl == null ? JSONObject.NULL : downloadUrl);
        }
        catch(Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Attempt to fetch the download URL of a video from the URL to the Moonflix page of the video.
     *
     * @param videoUrl URL to video on Moonflix - e.g "https://www.moonflix.co.nz/videos/car-ad-with-colin-meads"
     * @return Download URL - e.g "https://player.vimeo.com/video/501997772" or null
     */
    @Nullable
    private static String fetchVideoDownloadUrl(String videoUrl) {
        try {
            Element videoDetails = Jsoup.connect(videoUrl).get().selectFirst(".embedly-embed");

            if(videoDetails == null) {
                throw new Exception("Video not found!");
            }

            // //cdn.embedly.com/widgets/media.html?src=https://player.vimeo.com/video/417042475?app_id=122963&dntp=1&display_name=Vimeo&url=https://vimeo.com/417042475&image=https://i.vimeocdn.com/video/891123061_1280.jpg&key=96f1f04c5f4143bcb0f2e68c87d65feb&type=text/html&schema=vimeo
            String videoMeta = URLDecoder.decode(videoDetails.attr("src"), "UTF-8");

            Matcher matcher = Pattern.compile("https://player.vimeo.com/video/\\d+").matcher(videoMeta);

            if(!matcher.find()) {
                throw new Exception("Download URL not found!");
            }

            return videoMeta.substring(matcher.start(), matcher.end());
        }
        catch(Exception e) {
            return null;
        }
    }

    /**
     * Write the given content to a file.
     * This will create the file if it does not exist, or overwrite what
     * is in the file if it does.
     *
     * @param content  Content to write to file
     * @param filename File to write to - e.g "content.json"
     * @return Content successfully written to file
     */
    private static boolean writeToFile(String content, String filename) {
        try {
            FileWriter fileWriter = new FileWriter(filename);
            fileWriter.write(content);
            fileWriter.close();
            return true;
        }
        catch(IOException e) {
            return false;
        }
    }
}
