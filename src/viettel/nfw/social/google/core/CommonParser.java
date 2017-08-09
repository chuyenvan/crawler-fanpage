package viettel.nfw.social.google.core;

import java.net.MalformedURLException;
import java.net.URL;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import viettel.nfw.social.google.utils.GooglePlusURL;

public class CommonParser {

    private static final String TYPE_PERSON = "person";
    private static final String TYPE_PAGE = "page";

    public static String identifyProfileType(String objectId, Document profileDoc) throws MalformedURLException {

        String returnType = "";
        boolean existAdd = false;
        boolean existFollow = false;

        String div_dataowner = profileDoc.select("div[data-owner=" + objectId + "]").html();
        Document doc_div_content = Jsoup.parse(div_dataowner);

        // looking for link .../objectId/add
        Elements a_href = doc_div_content.select("a[href]");
        // looking for link .../objectId/follow
        Elements form_action = doc_div_content.select("form[action]");

        if (!a_href.isEmpty()) {
            String link_add = GooglePlusURL.BASE_URL + a_href.get(0).attr("href");
            URL url_add = new URL(link_add);
            String path = url_add.getPath();
            String pattern = "/app/basic/" + objectId + "/add";
            if (path.matches(pattern)) {
                existAdd = true;
            }
        }

        if (!form_action.isEmpty()) {
            String link_add = GooglePlusURL.BASE_URL + form_action.get(0).attr("action");
            URL url_add = new URL(link_add);
            String path = url_add.getPath();
            String pattern = "/app/basic/a/" + objectId + "/follow";
            if (path.matches(pattern)) {
                existFollow = true;
            }
        }

        if (existFollow) {
            returnType = TYPE_PAGE;
        } else if (existAdd && !existFollow) {
            returnType = TYPE_PERSON;
        }

        return returnType;
    }
}
