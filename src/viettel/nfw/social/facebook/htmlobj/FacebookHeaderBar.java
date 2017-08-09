package viettel.nfw.social.facebook.htmlobj;

import java.net.URI;
import java.util.Map;
import org.jsoup.nodes.Element;

/**
 *
 * @author duongth5
 */
public class FacebookHeaderBar {

    // current URI 
    public URI sourceUri;
    // Map links of account profile
    public Map<String, URI> mapUris;
    // search form in Header bar
    public Element searchForm;

    public FacebookHeaderBar() {
    }

}
