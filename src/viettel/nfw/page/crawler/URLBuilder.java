/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package viettel.nfw.page.crawler;

import java.io.FileNotFoundException;
import java.io.IOException;
import org.glassfish.jersey.internal.util.Base64;

/**
 *
 * @author hoangvv
 */
public class URLBuilder {

	private static final String URL_PAGE_FULL = ("https://www.facebook.com/pages_reaction_units/more/?page_id=__ID__&cursor=%7B%22timeline_cursor%22%3A%22timeline_unit%3A1%3A__TIME__%3A04611686018427387904%3A09223372036854775802%3A04611686018427387904%22%2C%22timeline_section_cursor%22%3A%7B%7D%2C%22has_next_page%22%3Atrue%7D&surface=www_pages_posts&unit_count=20&dpr=1&__user=0&__a=1&__dyn=7AmajEzUGByA5Q9UoHaEWC5EWq2WiWF3oyeqrWo8popyUW3F6xucxu13wFG2LzEjyR88xK5WAzEgDKuEjKeCxicxaFQEd8HDBxe6rCxaLGqu5omUOfz8lUlwQwEyp9Voybx24oqyU9omDx2r_xLgkBDxu26&__af=o&__req=9&__be=-1&__pc=EXP1%3ADEFAULT&__rev=2578936");
//	private static final String URL_PAGE_FULL = ("https://31.13.95.36/pages_reaction_units/more/?page_id=__ID__&cursor=%7B%22timeline_cursor%22%3A%22timeline_unit%3A1%3A__TIME__%3A04611686018427387904%3A09223372036854775802%3A04611686018427387904%22%2C%22timeline_section_cursor%22%3A%7B%7D%2C%22has_next_page%22%3Atrue%7D&surface=www_pages_home&unit_count=8&dpr=1&__user=0&__a=1&__dyn=7AmajEzUGByA5Q9UoHaEWC5EWq2WiWF3oyeqrWo8popyUW3F6xucxu13wFG2LzEjyR88xK5WAzEgDKuEjKeCxicxaFQEd8HDBxe6rCxaLGqu5omUOfz8lUlwQwEyp9Voybx24oqyU9omDx2r_xLgkBDxu26&__af=o&__req=9&__be=-1&__pc=EXP1%3ADEFAULT&__rev=2578936");
	private static final String URL_GROUP_FULL = "https://www.facebook.com/ajax/pagelet/generic.php/GroupEntstreamPagelet?dpr=1&no_script_path=1&data={\"last_view_time\":0,\"is_file_history\":null,\"is_first_story_seen\":true,\"story_index\":13,\"end_cursor\":\"__CURSOR__\",\"group_id\":__ID__,\"has_cards\":true,\"multi_permalinks\":[],\"trending_stories\":[]}&__user=0&__a=1&__dyn=aKhoFeyfyGmaomgDxyG8EiolzFEbFbGAdy8Z9LFwxBxCbzES2N6wAxu13wFG2K49UKbkwy8xa5WjzHz9XDG4XzE8EiGt0gKum4UpKq4GFFUkxvDAyXUG49e5o5ami9J7By8K48hxGbwYzoGr_gnHggKm7WAxmAiiamezES68G9z8Cqnh45EgAxmnBw&__af=i0&__req=jsonp_2&__be=-1&__pc=PHASED:DEFAULT&__rev=2778708&__adt=2";
//	private static final String URL_GROUP_FULL = "https://31.13.95.36/ajax/pagelet/generic.php/GroupEntstreamPagelet?dpr=1&no_script_path=1&data={\"last_view_time\":0,\"is_file_history\":null,\"is_first_story_seen\":true,\"story_index\":13,\"end_cursor\":\"__CURSOR__\",\"group_id\":__ID__,\"has_cards\":true,\"multi_permalinks\":[],\"trending_stories\":[]}&__user=0&__a=1&__dyn=aKhoFeyfyGmaomgDxyG8EiolzFEbFbGAdy8Z9LFwxBxCbzES2N6wAxu13wFG2K49UKbkwy8xa5WjzHz9XDG4XzE8EiGt0gKum4UpKq4GFFUkxvDAyXUG49e5o5ami9J7By8K48hxGbwYzoGr_gnHggKm7WAxmAiiamezES68G9z8Cqnh45EgAxmnBw&__af=i0&__req=jsonp_2&__be=-1&__pc=PHASED:DEFAULT&__rev=2778708&__adt=2";
	private static String Cursor = "__TIME__:__ID__:__ID__,0:";

	static String builderPage(String ID) throws FileNotFoundException, IOException {
		Long start = System.currentTimeMillis();
		return URL_PAGE_FULL.replace("__ID__", ID).replace("__TIME__", start.toString());
	}

	static String builderGroup(String ID) {
		Long start = System.currentTimeMillis();
		Cursor = Cursor.replace("__TIME__", start.toString()).replace("__ID__", ID);
		String CursorinBase64 = Base64.encodeAsString(Cursor);
		return URL_GROUP_FULL.replace("__ID__", ID).replace("__CURSOR__", CursorinBase64);
	}

	public static void main(String[] args) throws IOException {
		System.out.println(URLBuilder.builderPage("46889335859"));
	}

}
