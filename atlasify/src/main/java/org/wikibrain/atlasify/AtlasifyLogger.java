package org.wikibrain.atlasify;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Created by toby on 2/2/15.
 */
public class AtlasifyLogger {

    public static class logLogin{
        private String userId;
        private String browser;
        private String language;

        public logLogin(){

        }

        public logLogin(String userId, String browser, String language){
            this.userId = userId;
            this.browser = browser;
            this.language = language;
        }



        public String getUserId(){
            return userId;
        }

        public String getBrowser(){
            return browser;
        }

        public String getLanguage(){
            return language;
        }

    }

    public static class logQuery{
        private String userId;
        private String type;
        private String keyword;
        private String refSys;
        private String centroid;
        private String browser;
        private String language;
        private String ipAddress;
        private String ipLat;
        private String ipLon;
        private String ipOrg;
        private String ipCountry;
        private String ipCity;


        public logQuery(){

        }

        public logQuery(String userId, String type, String keyword, String refSys, String centroid, String browser, String language, String ipAddress){
            this.userId = userId;
            this.type = type;
            this.keyword = keyword;
            this.refSys = refSys;
            this.centroid = centroid;
            this.browser = browser;
            this.language = language;
            this.ipAddress = ipAddress;

        }

        public String getUserId(){
            return userId;
        }

        public String getType(){
            return type;
        }

        public String getKeyword(){
            return keyword;
        }

        public String getRefSys(){
            return refSys;
        }

        public String getCentroid(){
            return centroid;
        }

        public String getBrowser(){
            return browser;
        }

        public String getLanguage(){
            return language;
        }

        public String getIpAddress() { return ipAddress; }

        public String getIpLat() { return ipLat; }

        public String getIpLon() {
            return ipLon;
        }

        public String getIpOrg() {
            return ipOrg;
        }

        public String getIpCountry() {
            return ipCountry;
        }

        public String getIpCity() {
            return ipCity;
        }


    }

    public static class crowdSourceData {
        private String keyword;
        private String feature;
        private String srValue;
        private String explanation;

        public crowdSourceData() {

        }

        public crowdSourceData(String keyword, String feature, String srValue, String explanation) {
            this.keyword = keyword;
            this.feature = feature;
            this.srValue = srValue;
            this.explanation = explanation;
        }

        public String getKeyword() { return keyword; }

        public String getFeature() { return feature; }

        public String getSrValue() { return srValue; }

        public String getExplanation() { return explanation; }
    }

    public static class explanationsData {
        private static String keyword;
        private static String feature;
        private static String userId;
        private static List<JSONObject> explanationData;

        public explanationsData() {

        }

        public explanationsData(String keyword, String feature, String userId, List<JSONObject> explanationData) {
            this.keyword = keyword;
            this.feature = feature;
            this.userId = userId;
            this.explanationData = explanationData;
        }

        public static String getKeyword() {
            return keyword;
        }

        public static String getFeature() {
            return feature;
        }

        public static String getUserId() {
            return userId;
        }

        public static List<JSONObject> getExplanationData() {
            return explanationData;
        }
    }

    CSVWriter logLoginWriter;
    CSVWriter logQueryWriter;
    CSVWriter logCrowdSourceDataWriter;
    CSVWriter logExplanationsDataWriter;

    AtlasifyLogger(String loginPath, String queryPath, String crowdSourceDataPath, String explanationsDataPath)throws IOException{
        //Create ./log if not exists
        File logDir = new File("./log");
        if (!logDir.exists())
            logDir.mkdir();

        //Create new files if not exist
        File loginFile = new File(loginPath);
        File logQueryFile = new File(queryPath);
        File crowdSourceDataFile = new File(crowdSourceDataPath);
        File explanationsDataFile = new File(explanationsDataPath);
        loginFile.createNewFile();
        logQueryFile.createNewFile();
        crowdSourceDataFile.createNewFile();
        explanationsDataFile.createNewFile();

        logLoginWriter = new CSVWriter(new FileWriter(new File(loginPath), true), ',');
        logQueryWriter = new CSVWriter(new FileWriter(new File(queryPath), true), ',');
        logCrowdSourceDataWriter = new CSVWriter(new FileWriter(new File(crowdSourceDataPath), true), ',');
        logExplanationsDataWriter = new CSVWriter(new FileWriter(new File(explanationsDataPath), true), ',');
    }

    public void LoginLogger(logLogin data, String ip) throws IOException{
        String[] row = new String[5];
        row[0] = data.userId;
        row[1] = data.language;
        row[2] = data.browser;
        row[3] = ip;
        row[4] = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        logLoginWriter.writeNext(row);
        logLoginWriter.flush();
    }

    public void QueryLogger(logQuery data, String ip) throws IOException{
        String[] row = new String[10];
        row[0] = data.userId;
        row[1] = data.centroid;
        row[2] = data.refSys;
        row[4] = data.keyword;
        row[5] = data.type;
        row[6] = data.browser;
        row[7] = data.language;
        row[8] = data.ipAddress;
        row[9] = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        logQueryWriter.writeNext(row);
        logQueryWriter.flush();
    }

    public void CrowdSourceDataLogger(crowdSourceData data, String ip) throws IOException {
        String[] row = new String[6];
        row[0] = data.keyword;
        row[1] = data.feature;
        row[2] = data.srValue;
        row[3] = data.explanation;
        row[4] = ip;
        row[5] = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        logCrowdSourceDataWriter.writeNext(row);
        logCrowdSourceDataWriter.flush();
    }

    public void ExplanationsDataLogger(explanationsData data, String ip) throws IOException {
        String[] row = new String[6 + data.explanationData.size()];
        row[0] = data.userId;
        row[1] = data.keyword;
        row[2] = data.feature;
        row[3] = Integer.toString(data.explanationData.size());

        for (int i = 0; i < data.explanationData.size(); i++) {
            row[i+4] = data.explanationData.get(i).toString();
        }

        row[4 + data.explanationData.size()] = ip;
        row[5 + data.explanationData.size()] = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        logExplanationsDataWriter.writeNext(row);
        logExplanationsDataWriter.flush();
    }
}
