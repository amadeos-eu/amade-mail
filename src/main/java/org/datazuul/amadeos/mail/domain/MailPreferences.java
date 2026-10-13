package org.datazuul.amadeos.mail.domain;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Settings of the "Message View" category in Mail Options.
 *
 * @param preferHtml             show the HTML part of multipart/alternative messages by default
 * @param alwaysLoadRemoteImages load images from the internet in every message (tracking risk)
 * @param trustedImageSenders    senders whose remote images are always loaded
 * @param checkIntervalMinutes   interval for checking new mail, 0 disables it
 */
public record MailPreferences(boolean preferHtml, boolean alwaysLoadRemoteImages, Set<String> trustedImageSenders,
                              int checkIntervalMinutes) {

    public static final MailPreferences DEFAULTS = new MailPreferences(false, false, Set.of(), 5);

    public MailPreferences {
        TreeSet<String> normalized = new TreeSet<>();
        for (String sender : trustedImageSenders) {
            if (sender != null && !sender.isBlank()) normalized.add(sender.strip().toLowerCase(Locale.ROOT));
        }
        trustedImageSenders = Set.copyOf(normalized);
        checkIntervalMinutes = Math.max(0, checkIntervalMinutes);
    }

    public boolean loadsRemoteImagesFrom(String senderAddress) {
        return alwaysLoadRemoteImages
                || (senderAddress != null && trustedImageSenders.contains(senderAddress.toLowerCase(Locale.ROOT)));
    }

    public MailPreferences withTrustedSender(String senderAddress) {
        TreeSet<String> senders = new TreeSet<>(trustedImageSenders);
        senders.add(senderAddress);
        return new MailPreferences(preferHtml, alwaysLoadRemoteImages, senders, checkIntervalMinutes);
    }
}
