/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package vn.viettel.social.fb.test;

import java.net.MalformedURLException;
import vn.itim.detector.InputType;
import vn.itim.detector.Language;
import vn.itim.detector.LanguageDetector;

/**
 *
 * @author minhht1
 */
public class TestLanguageDetector {

	public static void main(String[] args) throws MalformedURLException {
		LanguageDetector languageDetector = new LanguageDetector();
		String text = "Bạn đang muốn khởi nghiệp nhưng không biết bắt đầu từ đâu?";
		Language language = languageDetector.detect(text, null, InputType.PLAIN);
		System.out.println(language);
	}
}
