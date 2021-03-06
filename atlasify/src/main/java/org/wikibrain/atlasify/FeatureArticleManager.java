package org.wikibrain.atlasify;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.apache.ApacheHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.testing.json.MockJsonFactory;
import com.google.gdata.client.ClientLoginAccountType;
import com.google.gdata.data.Person;
import com.google.api.client.json.jackson.JacksonFactory;

import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.client.spreadsheet.FeedURLFactory;

import com.google.gdata.client.spreadsheet.CellQuery;

import com.google.gdata.data.Feed;
import com.google.gdata.data.TextConstruct;
import com.google.gdata.data.extensions.Im;
import com.google.gdata.data.spreadsheet.*;

import com.google.gdata.data.Entry;

import com.google.gdata.util.common.xml.XmlWriter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.ArrayUtils;
import org.clapper.util.io.FileUtil;
import org.jooq.util.derby.sys.Sys;
import org.json.JSONArray;
import org.json.JSONObject;
import org.opengis.filter.spatial.Equals;
import org.openqa.selenium.*;
import org.openqa.selenium.Dimension;
import org.wikibrain.core.lang.LocalId;
import org.wikibrain.core.model.LocalPage;
import org.wikibrain.spatial.constants.RefSys;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.openqa.selenium.firefox.FirefoxBinary;
import org.openqa.selenium.firefox.FirefoxDriver;

import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;

import org.apache.commons.codec.binary.Base64;

/**
 * Created by Josh on 5/8/15.
 */
public class FeatureArticleManager {
    private enum ReferenceSystem {
        Geography,
        Chemistry,
        Politics,
        History;

        @Override
        public String toString() {
            if (this == Chemistry) {
                return "chemistry";
            } else if (this == Politics) {
                return "politics";
            } else if (this == History) {
                return "history";
            } else {
                return "geography";
            }
        }

        public int toInt() {
            switch (this) {
                case Geography:
                    return 1;
                case Chemistry:
                    return 2;
                case Politics:
                    return 3;
                case History:
                    return 4;
            }
            return 1;
        }
    }

    public static ReferenceSystem refSysString(String s) {
        if (s == null) {
            return ReferenceSystem.Geography;
        }

        s = s.trim();
        if (s.toLowerCase().startsWith("c") || s.startsWith("2")) {
            return ReferenceSystem.Chemistry;
        } else if (s.toLowerCase().startsWith("p") || s.startsWith("3")) {
            return ReferenceSystem.Politics;
        } else if (s.toLowerCase().startsWith("h") || s.startsWith("4")) {
            return ReferenceSystem.History;
        }
        return ReferenceSystem.Geography;
    }
    public static ReferenceSystem refSysInt(int i) {
        switch (i) {
            case 1:
                return ReferenceSystem.Geography;
            case 2:
                return ReferenceSystem.Chemistry;
            case 3:
                return ReferenceSystem.Politics;
            case 4:
                return ReferenceSystem.History;
        }
        return ReferenceSystem.Geography;
    }

    private class Article {
        // Parses a title and ref system from a string
        // Should be one of the formats:
        //  title:refSys
        //  title         (Defaults to Geography)
        Article(String data) {
            String value = data;
            String title = data;
            ReferenceSystem refSys = ReferenceSystem.Geography;

            int colonIndex = value.indexOf(":");
            if (colonIndex >= 0) {
                title = value.substring(0, colonIndex).trim();
                try {
                    title = resolveToWikipediaArticle(title);
                } catch (Exception e) {
                    System.out.println("Unable to resolve " + value + " to wikipedia article");
                    //e.printStackTrace();
                }
                if (title.length() <= 0) {
                    System.out.println("Invalid title " + value);
                }
                refSys = refSysString(value.substring(colonIndex + 1));
            }

            this.title = title;
            this.refSys = refSys;
        }

        Article(String title, ReferenceSystem refSys) {
            this.title = title;
            this.refSys = refSys;
        }

        private String title;
        private ReferenceSystem refSys;

        public String getTitle() {
            return title;
        }

        public ReferenceSystem getRefSys() {
            return refSys;
        }

        @Override
        public String toString() {
            return getTitle() + ":" + getRefSys().toString();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Article) {
                Article article = (Article)obj;
                return this.title.equals(article.title) && this.refSys == article.refSys;
            } else  {
                return this.equals(obj);
            }
        }

        @Override
        public int hashCode() {
            return title.hashCode() ^ refSys.hashCode();
        }
    }

    private class ArticleSection {
        ArticleSection (String title, List<Article> articles) {
            this.title = title;
            this.articles = articles;
        }

        private String title;
        private List<Article> articles;

        public String getTitle() {
            return title;
        }

        public List<Article> getArticles() {
            return articles;
        }

        public void addArticle(Article article) {
            getArticles().add(article);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(getTitle());
            sb.append(" : ");
            for (Article article : getArticles()) {
                sb.append(article.getTitle());
                sb.append(" - ");
                sb.append(article.getRefSys().toString());
                sb.append(", ");
            }

            sb.delete(sb.length()-2, sb.length());
            return sb.toString();
        }
    }

    private SpreadsheetService service;
    private FeedURLFactory factory;
    private List<ArticleSection> articleData;
    private JSONObject articleJSON;
    final private int MaximumNumberOfFeatureArticles = 10;
    private List<TrendingArticle> trendingArticles;

    // This continually reload the data at the time specified in the date parameter, the day, month, year don't matter
    // It will also refresh the data upon calling the constructor
    public FeatureArticleManager(Date date) throws Exception {
        this();

        final String masterSpreadsheetName = "Atlasify Featured Maps";
        // Load the data in 5 min, this is hopefully enough time for the server to load
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.schedule(new Runnable() {
                               @Override
                               public void run() {
                                   try {
                                       loadSpreadSheetData(masterSpreadsheetName);
                                   } catch (Exception e) {
                                       System.out.println("Error retrieving new featured article data");
                                       e.printStackTrace();
                                   }
                               }
                           }, 5, TimeUnit.MINUTES);

                Date current = new Date();
        date.setYear(current.getYear());
        date.setMonth(current.getMonth());
        date.setDate(current.getDate());
        if (date.getTime() < current.getTime()) {
            date.setDate(current.getDate() + 1);
        }

        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    loadSpreadSheetData(masterSpreadsheetName);
                } catch (Exception e) {
                    System.out.println("Error retrieving new featured article data");
                    e.printStackTrace();
                }
            }
        }, (date.getTime() - current.getTime())/1000, 24*60*60, TimeUnit.SECONDS);
    }
    private FeatureArticleManager() throws Exception {
        // This is using a development account part of atlasify@gmail.com
        String emailAddress = "152281337822-njvo1usnct105ce311asssgvpelfs6ck@developer.gserviceaccount.com";
        JsonFactory JSON_FACTORY = new JacksonFactory();
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        Collection<String> scope = Arrays.asList("http://spreadsheets.google.com/feeds/");
        GoogleCredential credential = new GoogleCredential.Builder()
                .setTransport(httpTransport)
                .setJsonFactory(JSON_FACTORY)
                .setServiceAccountId(emailAddress)
                .setServiceAccountPrivateKeyFromP12File(new File("atlasify/Atlasify-UMN-6e5cbb645a7f.p12"))
                .setServiceAccountScopes(scope)
                .build();
        factory = FeedURLFactory.getDefault();
        service = new SpreadsheetService("gdata-sample-spreadhsheetindex");
        service.setOAuth2Credentials(credential);
        trendingArticles = new ArrayList<TrendingArticle>();
    }

    public List<SpreadsheetEntry> getSpreadsheetEntries() throws Exception {
        SpreadsheetFeed feed = service.getFeed(
                factory.getSpreadsheetsFeedUrl(), SpreadsheetFeed.class);
        return feed.getEntries();
    }

    public List<WorksheetEntry> getWorksheetEntries(SpreadsheetEntry spreadsheet)
            throws Exception {
        return spreadsheet.getWorksheets();
    }

    public List<ArticleSection> getSheadsheetData(WorksheetEntry worksheet)
            throws Exception {

        // Get the appropriate URL for a cell feed
        URL cellFeedUrl = worksheet.getCellFeedUrl();

        // Create a query for the top row of cells only (1-based)
        CellQuery cellQuery = new CellQuery(cellFeedUrl);

        // Get the cell feed matching the query
        CellFeed cellFeed = service.query(cellQuery, CellFeed.class);

        // Get the cell entries from the feed
        List<CellEntry> cellEntries = cellFeed.getEntries();
        List<ArticleSection> sections = new ArrayList<ArticleSection>();
        for (CellEntry entry : cellEntries) {
            // Get the cell element from the entry
            Cell cell = entry.getCell();
            if (cell.getRow() == 1) {
                continue; // This is the header row, so we don't need to keep it
            }

            if (cell.getCol() == 1) {
                ArticleSection section = new ArticleSection(cell.getValue(), new ArrayList<Article>());
                sections.add(section);
            } else  {
                ArticleSection section = sections.get(sections.size() - 1);
                section.addArticle(new Article(cell.getValue()));
            }
        }

        return sections;
    }

    private String resolveToWikipediaArticle(String s) throws Exception {
        LocalId id = AtlasifyResource.wikibrainPhaseResolution(s);
        LocalPage page = AtlasifyResource.lpDao.getById(id);
        return page.getTitle().getCanonicalTitle();
    }

    // Filename should not include extension in the filename, we will automatically
    // make it a .png (We are making images that are HDPI and will name them appropriately)
    private void createImageForArticle(Article article, String filename) throws IOException {
        int      DISPLAY_NUMBER  = 99;
        String   XVFB            = "Xvfb";
        String   XVFB_COMMAND    = XVFB + " :" + DISPLAY_NUMBER;
        String   URL             = AtlasifyLauncher.externalURL + "?query=" + URLEncoder.encode(article.getTitle()).replace("+", "%20") + "&category=" + article.getRefSys().toInt();

        Process p = Runtime.getRuntime().exec(XVFB_COMMAND);
        FirefoxBinary firefox = new FirefoxBinary();
        firefox.setEnvironmentProperty("DISPLAY", ":" + DISPLAY_NUMBER);
        FirefoxDriver driver = new FirefoxDriver(firefox, null);
        // Would be nice to increase size for hdpi devices
        int imageWidth  = 2 * 125;
        int imageHeight = 2 * 85;
        int cropTop = 50;
        int cropBottom = 55;
        int cropLeft = 45;
        int cropRight = cropLeft;

        // Set window size
        int windowWidth = 4 * imageWidth + cropLeft + cropRight;
        int windowHeight = 4 * imageHeight + cropTop + cropBottom;
        driver.manage().window().setSize(new Dimension(windowWidth, windowHeight));
        driver.get(URL);

        // Clean up the page to make sure nothing will
        driver.executeScript("if (showingAutocompleteOverlay) hideAutocompleteOverlay();" +
                "if (!legendHidden) toggleLegend();" +
                "if (currentlyShowingExplanationsInfoPopover) removeExplanationsInfoPopover();" +
                "if (currentlyShowingRefSysPopover) hideRefSysInfoPopover();");
        try {
            // This should allow for the webpage to load
            Thread.sleep(10000);
        } catch (InterruptedException e) {

        }

        try {
            File scrFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            BufferedImage image = ImageIO.read(scrFile);

            // Crop out UI
            image = image.getSubimage(cropLeft, cropTop, image.getWidth() - cropLeft - cropRight, image.getHeight() - cropTop - cropBottom);

            // Crop to correct aspect ratio
            if ((double)imageWidth / (double)imageHeight < (double)image.getWidth() / (double)image.getHeight()) {
                // The image is wider than it needs to be
                int scaledImageWidth = image.getHeight() * imageWidth / imageHeight ;
                int widthOffset = (image.getWidth() - scaledImageWidth) / 2;
                image = image.getSubimage(widthOffset, 0, scaledImageWidth, image.getHeight());
            } else {
                // The image is taller than it needs to be
                int scaledImageHeight = image.getWidth() * imageHeight / imageWidth;
                int heightOffset = (image.getHeight() - scaledImageHeight) / 2;
                image = image.getSubimage(0, heightOffset, image.getWidth(), scaledImageHeight);
            }

            // Scale to correct size
            BufferedImage newImage = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_USHORT_565_RGB);
            Graphics2D g = newImage.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, 0, 0, imageWidth, imageHeight, null);
            g.dispose();
            ImageIO.write(newImage, "png", new File(filename + "@2x.png"));

            // Create low resolution image
            BufferedImage lowResImage = new BufferedImage(imageWidth/2, imageHeight/2, newImage.getType());
            g = lowResImage.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(newImage, 0, 0, imageWidth/2, imageHeight/2, null);
            g.dispose();
            ImageIO.write(lowResImage, "png", new File(filename + ".png"));
        } finally {
            driver.close();
            p.destroy();
        }
    }

    private String featureArticleFolder = "featured-articles/";
    private void loadSpreadSheetData(String masterSpreadsheetName) throws Exception {
        SpreadsheetEntry masterSpreadsheet = null;
        for (SpreadsheetEntry spreadsheet : getSpreadsheetEntries()) {
            if (spreadsheet.getTitle().getPlainText().equals(masterSpreadsheetName)) {
                masterSpreadsheet = spreadsheet;
            }
        }

        WorksheetEntry worksheet = null;
        if (masterSpreadsheet != null) {
            // We will always use the first sheet, could change later if needed
             worksheet = getWorksheetEntries(masterSpreadsheet).get(0);
            if (worksheet == null) {
                System.out.println("Unable to load worksheet from master spreadsheet");
                throw new Exception();
            }
        } else {
            System.out.println("Could not find master spreadsheet named \"" + masterSpreadsheetName + "\"");
            throw new Exception();
        }

        // Make sure the directory exists
        File articleDir = new File(featureArticleFolder);
        if (!articleDir.exists()) {
            articleDir.mkdir();
        }

        syncTrendingData(worksheet);
        Thread.sleep(10000);
        syncFeaturedData(worksheet);
    }

    private void syncFeaturedData(WorksheetEntry worksheet) throws Exception {
        // Print out all the loaded data, generate the images if necessary
        System.out.println("BEGIN loading feature article data");
        List<ArticleSection> sections = getSheadsheetData(worksheet);
        System.out.println("Received spreadsheet data from Google sheets");
        System.out.println("Generating images");

        for (ArticleSection section : sections) {
            System.out.println("\t" + section);
            for (Article article : section.getArticles()) {
                String filename = featureArticleFolder + article.toString();
                File image = new File(filename  + ".png");
                if (image.exists() && image.isFile()) {
                    System.out.println("\t\tFound image for article " + article.toString());
                } else {
                    // No file, we should generate it
                    try {
                        createImageForArticle(article, filename);
                        image = new File(filename  + ".png");
                        assert image.exists() : image.isFile();
                        System.out.println("\t\tSuccessfully generated image for article " + article.toString());
                    } catch (Exception e) {
                        System.out.println("\t\tUnable to generate image for article " + article.toString());
                        e.printStackTrace();
                        continue;
                    }
                }
            }
        }

        JSONObject json = new JSONObject();
        JSONArray jsonSections = new JSONArray();
        for (ArticleSection section : sections) {
            JSONObject jsonSection = new JSONObject();
            jsonSection.put("title", section.getTitle());
            JSONArray jsonArticles = new JSONArray();

            for (Article article : section.getArticles()) {
                JSONObject jsonArticle = new JSONObject();
                jsonArticle.put("title", article.getTitle());
                jsonArticle.put("refSys", article.getRefSys().toInt());
                jsonArticles.put(jsonArticle);
            }

            jsonSection.put("articles", jsonArticles);
            jsonSections.put(jsonSection);
        }

        json.put("results", jsonSections);

        articleData = sections;
        articleJSON = json;
        System.out.println("FINISHED loading feature article data");
    }

    private void syncTrendingData(WorksheetEntry worksheet) throws Exception {
        System.out.println("BEGIN Updating Trending Data");
        ArticleSection currentTrendingData = getSheadsheetData(worksheet).get(0);
        Collections.sort(trendingArticles);
        Collections.reverse(trendingArticles);
        TrendingArticle[] currentTrendingArticles = trendingArticles.toArray(new TrendingArticle[0]);
        Set<Article> currentlyStoredArticle = new HashSet<Article>();
        trendingArticles = new ArrayList<TrendingArticle>();

        URL trendingFeedUrl = worksheet.getCellFeedUrl();
        CellQuery trendingQuery = new CellQuery(trendingFeedUrl);
        CellFeed trendingFeed = service.query(trendingQuery, CellFeed.class);
        int processedCells = 0;
        // When we find a duplicate item, we then will delete all remaining cells
        // And use the "fill" algorithm below
        boolean foundDuplicateItem = false;
        for (CellEntry cellEntry : trendingFeed.getEntries()) {
            Cell cell = cellEntry.getCell();
            if (cell.getRow() != 2) {
                // We only need to update the trending row
                continue;
            }
            if (cell.getCol() == 1) {
                // This should be the treading row
                continue;
            }

            // Indexing Begins from 1
            int index = cell.getCol() - 1;
            if (index > MaximumNumberOfFeatureArticles || foundDuplicateItem) {
                cellEntry.delete();
                continue;
            }

            // Set the appropriate value
            if (index <= currentTrendingArticles.length) {
                Article article = currentTrendingArticles[index - 1].article;
                // The current trending articles should all be unique
                cellEntry.changeInputValueLocal(article.title + ":" + article.getRefSys().toString());
                System.out.println("\t" + article.title + ":" + article.getRefSys().toString());
                cellEntry.update();
                currentlyStoredArticle.add(article);
                processedCells++;
            } else {
                index -= currentTrendingArticles.length;
                // Make sure there is enough data
                if (index > currentTrendingData.getArticles().size()) {
                    cellEntry.delete();
                    continue;
                }

                Article article = currentTrendingData.getArticles().get(index - 1);
                if (currentlyStoredArticle.contains(article)) {
                    foundDuplicateItem = true;
                    cellEntry.delete();
                    continue;
                }

                cellEntry.changeInputValueLocal(article.title + ":" + article.getRefSys().toString());
                System.out.println("\t" + article.title + ":" + article.getRefSys().toString());
                cellEntry.update();
                processedCells++;
            }
        }

        int numberOfCellsWithData = currentTrendingArticles.length + currentTrendingData.getArticles().size();
        int currentIndex = processedCells; // +1 since each row begins with a header, +1 since the rows are indexed from 1
        while (processedCells < MaximumNumberOfFeatureArticles && currentIndex < numberOfCellsWithData) {
            // We should add more cells
            currentIndex++;
            int index = currentIndex;
            CellEntry cellEntry = new CellEntry(2, processedCells + 2, "");
            cellEntry = trendingFeed.insert(cellEntry);

            // Set the appropriate value
            if (index <= currentTrendingArticles.length) {
                Article article = currentTrendingArticles[index - 1].article;
                // The current trending articles should all be unique
                cellEntry.changeInputValueLocal(article.title + ":" + article.getRefSys().toString());
                System.out.println("\t" + article.title + ":" + article.getRefSys().toString());
                cellEntry.update();
                processedCells++;
            } else {
                index -= currentTrendingArticles.length;
                // Make sure there is enough data
                if (index > currentTrendingData.getArticles().size()) {
                    cellEntry.delete();
                    continue;
                }

                Article article = currentTrendingData.getArticles().get(index - 1);
                if (currentlyStoredArticle.contains(article)) {
                    continue;
                }

                cellEntry.changeInputValueLocal(article.title + ":" + article.getRefSys().toString());
                System.out.println("\t" + article.title + ":" + article.getRefSys().toString());
                cellEntry.update();
                processedCells++;
            }
        }

        System.out.println("FINISH Updating Trending Data");
    }

    private class TrendingArticle implements Comparator<TrendingArticle>, Comparable<TrendingArticle> {
        public TrendingArticle(Article article) {
            this.article = article;
            this.count = 0;
        }
        private Article article;
        private int count;

        public Article getArticle() {
            return article;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public int compareTo(TrendingArticle article){
            return ((Integer)this.count).compareTo(article.count);
        }

        public int compare(TrendingArticle article1, TrendingArticle article2) {
            return article1.compareTo(article2);
        }

        @Override
        public String toString() {
            return "[" + count + "] " + article.toString();
        }
    }
    // Call this anytime a map is viewed to update the trending data
    public void viewedArticle(String s, ReferenceSystem refSys) {
        Article article = new Article(s, refSys);
        System.out.println("Adding trend: " + article.toString());

        TrendingArticle alreadyStoredArticle = null;
        for (TrendingArticle storedArticles : trendingArticles) {
            if (storedArticles.article.equals(article)) {
                alreadyStoredArticle = storedArticles;
                break;
            }
        }

        if (alreadyStoredArticle != null) {
            // Update the already stored article
            trendingArticles.remove(alreadyStoredArticle);
            alreadyStoredArticle.setCount(1 + alreadyStoredArticle.getCount());
            trendingArticles.add(alreadyStoredArticle);
        } else {
            // Add the article
            trendingArticles.add(new TrendingArticle(article));
        }
    }
    public JSONObject getArticleJSON() {
        return articleJSON;
    }

    public byte[] getImageFor(String s, int RefSys, int resolution) throws IOException {
        Article article = new Article(s, refSysInt(RefSys));

        File image = null;
        if (resolution > 1) {
            // Try to get high resolution image
            String filename = featureArticleFolder + article.toString() + "@2x.png";
            image = new File(filename);
            if (!image.exists() || !image.isFile())
                image = null;
        }
        if (image == null) {
            String filename = featureArticleFolder + article.toString() + ".png";
            image = new File(filename);
        }
        return Base64.encodeBase64(FileUtils.readFileToByteArray(image));
    }
}
