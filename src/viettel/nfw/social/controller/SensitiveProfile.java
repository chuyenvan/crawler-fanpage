package viettel.nfw.social.controller;

/**
 *
 * This class for JSON Object Encode/ Decoder so Don't final its fields
 *
 * @author duongth5
 *
 */
public class SensitiveProfile {

    public String url;
    public double sensitiveScore;

    public SensitiveProfile() {
    }

    public SensitiveProfile(String url, double sensitiveScore) {
        this.url = url;
        this.sensitiveScore = sensitiveScore;
    }

    @Override
    public String toString() {
        return url + " " + sensitiveScore;
    }
}
