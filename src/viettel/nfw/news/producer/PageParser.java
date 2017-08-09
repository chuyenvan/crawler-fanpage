/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package viettel.nfw.news.producer;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import viettel.nfw.social.model.facebook.FacebookObject;
import viettel.nfw.social.model.facebook.Post;
import viettel.nfw.social.model.facebook.Profile;

/**
 *
 * @author hoangvv
 */
public class PageParser {
    
    public PageParser() {
    }
    
    public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, Exception {
        List<String> listofID = new LinkedList<>();
        String[] paths;
        File file = new File("H:/tmp");
        paths = file.list();
        for (String path : paths) {
            path = regrex(path);
            listofID.add(path);
        }
//        for (String ID : listofID) {
//            parse(ID);
//        }
        //   parseLike("");
        // parseTimeline("390567570966109");
        test();
        // sendPost();
    }
    
    private static void parseTimeline(String ID) throws FileNotFoundException, IOException, ParseException {
        String HTML_ADD = "<!DOCTYPE html><html><head><meta charset=\"utf-8\" /></head><body>";
        String HTML_END = "</body></html>";
        FacebookObject fbobject = new FacebookObject();
        Profile profile = new Profile();
        profile.setId(ID); //set ID for profile of Page
        JSONParser json = new JSONParser();
        FileReader fileJson = new FileReader("H:/tmp/" + ID + ".json");
        JSONObject obj = (JSONObject) json.parse(fileJson);
        JSONObject obj1 = (JSONObject) obj.get("jsmods");
        JSONArray array = (JSONArray) obj1.get("require");
        int i = 1;
        List<Post> listofPost = new LinkedList<>();
        for (Object array3 : array) {
            JSONArray array1 = (JSONArray) array3;
            if ("UFIController".equals(array1.get(0).toString())) {
                JSONArray array2 = (JSONArray) array1.get(3);
                JSONObject obj2 = (JSONObject) array2.get(1);
                String obj3 = (String) obj2.get("feedcontext");
                String obj5 = (String) obj2.get("ftentidentifier");
                String obj7 = (String) obj2.get("ownerName");
                JSONObject obj8 = (JSONObject) obj2.get("loggedOutLinkConfig");
                System.out.println(obj8);
                JSONObject obj4 = (JSONObject) json.parse(obj3);
                //   JSONObject obj6 = (JSONObject) obj4.get("shimparams");
                Long author = (Long) obj4.get("profile_id");
                Post post = new Post();
                post.setId(obj5);
                post.setActorProfileId(author.toString());
                //   System.out.println(post.getId());
                post.setActorProfileDisplayName(obj7);
                listofPost.add(post);
                //  System.out.println(obj3);
            }
        }

        //        html = HTML_ADD + html + HTML_END;
//        File file = new File("H:/html/" + ID + ".html");
//        if (!file.exists()) {
//            file.createNewFile();
//        }
//        FileOutputStream fop = new FileOutputStream(file);
//        fop.write(html.getBytes());
//        fop.flush();
//        fop.close();
    }
    
    private static void parseLike(String ID) throws FileNotFoundException, IOException, ParseException {
        String HTML_ADD = "<!DOCTYPE html><html><head><meta charset=\"utf-8\" /></head><body>";
        String HTML_END = "</body></html>";
        FacebookObject fbobject = new FacebookObject();
        Profile profile = new Profile();
        profile.setId(ID); //set ID for profile of Page
        JSONParser json = new JSONParser();
        FileReader fileJson = new FileReader("H:/like.json");
        JSONObject obj = (JSONObject) json.parse(fileJson);
        JSONObject obj1 = (JSONObject) obj.get("jsmods");
        JSONArray array = (JSONArray) obj1.get("markup");
        JSONArray array1 = (JSONArray) array.get(0);
        JSONObject obj2 = (JSONObject) array1.get(1);
        String html = (String) obj2.get("__html");
        html = HTML_ADD + html + HTML_END;
        File file = new File("H:/html/Like.html");
        if (!file.exists()) {
            file.createNewFile();
        }
        FileOutputStream fop = new FileOutputStream(file);
        fop.write(html.getBytes());
        fop.flush();
        fop.close();
        
    }
    
    public static String regrex(String line) {
        String fixFirst = null;
        String patternFirst = "(\\d+)";
        Pattern PattFirst = Pattern.compile(patternFirst);
        Matcher MatFirst = PattFirst.matcher(line);
        if (MatFirst.find()) {
            fixFirst = MatFirst.group(0);
        }
        return fixFirst;
    }
    private static final String USER_AGENT = "Mozilla/5.0";
    
    private static void sendPost() throws Exception {
        String dynamic = "aKTyAW8-aFoFxp2uu4aEyAy965oWqfoOfAKGgS8Vpt9LFGA4XmnxurGbGE8F8Z6UF2UF7yWCGq4KE-VFHyubCG22aUKFUKipiei8DyElWjzHBAVtatojF3Ami4FeEKqFSczQ2miCUKu6rCAK9Gl4yrUW5-uifz98KjDgHy4mEgzWyoK49kF9ah8zy448hyWgGFK2laaDLVoGp_gV6iQ4bBBUS9yo-VK";
        String param_Feed = "{\"reaction_unit_data\":{\"logging_data\":{\"impression_info\":\"eyJmIjp7Iml0ZW1fY291bnQiOiIxIn19\",\"surface\":\"www_pages_posts\",\"interacted_story_type\":\"688092377989036\",\"session_id\":\"5e47f3aca954bcaef5554773b0bff0b4\"}},\"fbfeed_context\":true,\"location_type\":36,\"is_pinned_post\":false,\"can_moderate_timeline_story\":false,\"profile_id\":390567570966109,\"is_published_from_composer\":false,\"story_width\":502,\"outer_object_element_id\":\"u_o_0\",\"object_element_id\":\"u_o_0\",\"is_ad_preview\":false,\"is_editable\":false,\"mall_how_many_post_comments\":2,\"shimparams\":{\"page_type\":16,\"actor_id\":390567570966109,\"story_id\":1256422957713895,\"ad_id\":0,\"_ft_\":\"\",\"location\":\"homepage_stream\"}}";
        String url = "https://www.facebook.com/ajax/ufi/comment_fetch.php?dpr=1";
        URL obj = new URL(url);
        System.setProperty("https.proxySet", "true");
        System.setProperty("https.proxyHost", "203.113.152.1");
        System.setProperty("https.proxyPort", "7020");
        HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();
        //add reuqest header
        con.setRequestMethod("POST");
        con.setRequestProperty("accept-language", "en-US,en;q=0.5");
        con.setRequestProperty("content-type", "application/x-www-form-urlencoded");
        con.setRequestProperty("user-agent", USER_AGENT);
        con.setRequestProperty("cookie", "datr=MajjV22L31tpAsCK1rm3_9_9; sb=PqjjVxThD6NWW-6FjXbilHAW; locale=en_GB; fr=0x8jZ4pgbAwIAIWTy..BX9NcS.h2.AAA.0.0.BX9NcS.AWU79YyK");
        
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ft_ent_identifier", "1256481637708027");
        params.put("length", "33");
        params.put("feed_context", param_Feed);
        params.put("__user", "0");
        params.put("__a", "1");
        params.put("__dyn", dynamic);
        
        String urlParameters = "";

        // Send post request
        con.setDoOutput(true);
        DataOutputStream wr = new DataOutputStream(con.getOutputStream());
        wr.writeBytes(urlParameters);
        wr.flush();
        wr.close();
        
        int responseCode = con.getResponseCode();
        System.out.println("\nSending 'POST' request to URL : " + url);
        System.out.println("Post parameters : " + urlParameters);
        System.out.println("Response Code : " + responseCode);
        
        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();
        
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        //print result
        System.out.println(response.toString());
        
    }
    
    private static void test() throws MalformedURLException, UnsupportedEncodingException, IOException {
        String dynamic = "7AzHK4GgN1t2u6XgfpEbFbGEW8xdLFwgoqwXCxucxu13wm8gxZ3ocWwAyUG4XzEa8uwh9VoboG5olwIyp8swJwpV9Uqx24o9E5mqm7Q58S5U9E";
//  String dynamic = "aKTyAW8-aFoFxp2uu4aEyAy965oWqfoOfAKGgS8Vpt9LFGA4XmnxurGbGE8F8Z6UF2UF7yWCGq4KE-VFHyubCG22aUKFUKipiei8DyElWjzHBAVtatojF3Ami4FeEKqFSczQ2miCUKu6rCAK9Gl4yrUW5-uifz98KjDgHy4mEgzWyoK49kF9ah8zy448hyWgGFK2laaDLVoGp_gV6iQ4bBBUS9yo-VK";
        String dyna = "7AmajEzUGByA5Q9UoHaEWC5EWq2WiWF3oyeqrWo8popyUW3F6xucxu13wFG2LzEjyR88xK5WAzEgDKuEjKeCxicxaFQEd8HDBxe6rCxaLGqu5omUOfz8lUlwQwEyp9Voybx24oqyU9omDx2r_xLgkBDxu26";
        String parame = "ft_ent_identifier=540355862764421&viewas&source=2&offset=33&length=4&orderingmode=chronological&feed_context=%7B%22is_viewer_page_admin%22%3Afalse%2C%22is_notification_preview%22%3Afalse%2C%22autoplay_with_channelview_or_snowlift%22%3Afalse%2C%22fbfeed_context%22%3Atrue%2C%22location_type%22%3A5%2C%22outer_object_element_id%22%3A%22u_0_9%22%2C%22object_element_id%22%3A%22u_0_9%22%2C%22is_ad_preview%22%3Afalse%2C%22is_editable%22%3Afalse%2C%22mall_how_many_post_comments%22%3A2%2C%22story_width%22%3A502%2C%22shimparams%22%3A%7B%22page_type%22%3A16%2C%22actor_id%22%3A100003700366245%2C%22story_id%22%3A0%2C%22ad_id%22%3A0%2C%22_ft_%22%3A%22%22%2C%22location%22%3A%22permalink%22%7D%7D&numpagerclicks&av=&__user=0&__a=1&__dyn=7AzHK4GgN1t2u6XomwBCwKAKGzEy4S-C11xG3Kq2i5U4e1ox27RyUcWwAyUG4XzEa8uwh9VoboG5olwIyp8-cwJwpV9Uqx24o9E5mqm7Q59pUnwCzU&__af=o&__req=2&__be=-1&__pc=EXP1%3ADEFAULT&lsd=AVqpZFr5&__rev=2611159";
//  String param_Feed = "{\"reaction_unit_data\":{\"logging_data\":{\"impression_info\":\"eyJmIjp7Iml0ZW1fY291bnQiOiIxIn19\",\"surface\":\"www_pages_posts\",\"interacted_story_type\":\"688092377989036\",\"session_id\":\"5e47f3aca954bcaef5554773b0bff0b4\"}},\"fbfeed_context\":true,\"location_type\":36,\"is_pinned_post\":false,\"can_moderate_timeline_story\":false,\"profile_id\":390567570966109,\"is_published_from_composer\":false,\"story_width\":502,\"outer_object_element_id\":\"u_o_0\",\"object_element_id\":\"u_o_0\",\"is_ad_preview\":false,\"is_editable\":false,\"mall_how_many_post_comments\":2,\"shimparams\":{\"page_type\":16,\"actor_id\":390567570966109,\"story_id\":1256422957713895,\"ad_id\":0,\"_ft_\":\"\",\"location\":\"homepage_stream\"}}";
        String param_Feed = "{\"profile_id\":100003700366245,\"story_width\":513,\"fbfeed_context\":true,\"location_type\":10,\"outer_object_element_id\":\"tl_unit_1302185720303855516\",\"object_element_id\":\"tl_unit_1302185720303855516\",\"is_ad_preview\":false,\"is_editable\":false,\"mall_how_many_post_comments\":2,\"shimparams\":{\"page_type\":16,\"actor_id\":100003700366245,\"story_id\":898683110265026,\"ad_id\":0,\"_ft_\":\"\",\"location\":\"timeline\"},\"story_id\":\"u_jsonp_2_6\",\"caret_id\":\"u_jsonp_2_7\"}";
        String url1 = "https://www.facebook.com/ajax/ufi/comment_fetch.php?dpr=1";
        URL url = new URL(url1);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ft_ent_identifier", "540355862764421");
        params.put("viewas", "");
        params.put("source", 2);
        params.put("length", 33);
        params.put("orderingmode", "chronological");
        params.put("feed_context", param_Feed);
        params.put("__a", 1);
        params.put("__dyn", "7AzHK4GgN1t2u6XomwBCwKAKGzEy4S-C11xG3Kq2i5U4e1ox27RyUcWwAyUG4XzEa8uwh9VoboG5olwIyp8-cwJwpV9Uqx24o9E5mqm7Q59pUnwCzU");
        params.put("__af", "o");
        params.put(("__req"), "9");
        params.put("__be", "-1");
        params.put("__pc", "EXP1:DEFAULT");
        params.put("__rev", "2602612");
        params.put("lsd", "AVqpZFr5");
        //    params.put("lsd", "AVoh0Eri");
        StringBuilder postData = new StringBuilder();
        for (Map.Entry<String, Object> param : params.entrySet()) {
            if (postData.length() != 0) {
                postData.append('&');
            }
            postData.append(param.getKey());
            postData.append('=');
            postData.append(param.getValue());
        }
        byte[] postDataBytes = postData.toString().getBytes();
        System.setProperty("https.proxySet", "true");
        System.setProperty("https.proxyHost", "203.113.152.1");
        System.setProperty("https.proxyPort", "7020");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("accept-language", "vi-VN,vi;q=0.8,fr-FR;q=0.6,fr;q=0.4,en-US;q=0.2,en;q=0.2");
        conn.setRequestProperty("content-type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("user-agent", USER_AGENT);
        conn.setRequestProperty("cookie", "datr=MajjV22L31tpAsCK1rm3_9_9; locale=vi_VN; sb=PqjjVxThD6NWW-6FjXbilHAW; lu=gAvYogb0lWpxY0D9daogfkMg; reg_fb_gate=https%3A%2F%2Fwww.facebook.com%2F%3Fstype%3Dlo%26jlou%3DAfcU2ZityNkxTu520_XFkUPIdO3HGdVro-V8_Mky97UpFJUs-no0dNU2Bg6rNZKoCZagQwrzqkDOj3GIj22MeqIisSCiUxHa0Ntvsy9EG8SVlA%26smuh%3D34193%26lh%3DAc-upkW0clxlJXrP; reg_fb_ref=https%3A%2F%2Fwww.facebook.com%2Fthu0cl4o; fr=0mSq6DQ68jng0RyPT.AWU__H8xocMLxlGUvLNzK7LFoAg.BX46gx.PB.Ffr.0.0.BX-xzX.AWWb9jRw; _js_reg_fb_ref=https%3A%2F%2Fwww.facebook.com%2Fphoto.php%3Ffbid%3D540355862764421%26set%3Da.179073432226001.41423.100003700366245%26type%3D3; wd=637x685; act=1476074726681%2F0; x-src=%2Fphoto.php%7Cstream_pagelet");
        
        conn.setDoOutput(true);
//        conn.getOutputStream().write(parame.getBytes());
        conn.getOutputStream().write(postDataBytes);
        Reader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        
        for (int c; (c = in.read()) >= 0;) {
            System.out.print((char) c);
        }
    }
}
