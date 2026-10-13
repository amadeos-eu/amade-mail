package org.datazuul.amadeos.mail.ui.view;

import org.xhtmlrenderer.resource.ImageResource;
import org.xhtmlrenderer.swing.AWTFSImage;
import org.xhtmlrenderer.swing.NaiveUserAgent;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

/**
 * Resource loader for the HTML view that never touches the network: images come only from the
 * message itself (cid:) or from remote images the presenter downloaded beforehand, after the user
 * allowed it. Everything else resolves to nothing.
 */
final class MailUserAgent extends NaiveUserAgent {
    private volatile Map<String, byte[]> resources = Map.of();

    void setResources(Map<String, byte[]> resources) {
        this.resources = Map.copyOf(resources);
    }

    @Override
    public String resolveURI(String uri) {
        // Keep URIs as they are; there is no base document to resolve against.
        return uri == null ? null : uri.strip();
    }

    @Override
    public ImageResource getImageResource(String uri) {
        byte[] data = uri == null ? null : resources.get(uri);
        if (data != null) {
            try {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
                if (image != null) return new ImageResource(uri, AWTFSImage.createImage(image));
            } catch (IOException | RuntimeException e) {
                // broken image: fall through to the empty resource
            }
        }
        return new ImageResource(uri, null);
    }
}
