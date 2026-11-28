package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.SettingsCardsPresenter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SettingsItem {
    public final String title;
    public final Runnable onClick;
    public int imageResId;
    private String mDescription;
    private SettingsPanel mPanel;
    private SettingsCardsPresenter.Cards mCards;
    private final List<Link> mLinks = new ArrayList<>();

    /**
     * An address of a card, shown as a QR code on a long press of the card (see SettingsLinksPresenter)
     */
    public static class Link {
        public final String title;
        public final String url;

        public Link(String title, String url) {
            this.title = title;
            this.url = url;
        }
    }

    public SettingsItem(String title, Runnable onClick) {
        this(title, onClick, -1);
    }

    public SettingsItem(String title, Runnable onClick, int imageResId) {
        this.title = title;
        this.onClick = onClick;
        this.imageResId = imageResId;
    }

    /**
     * What the card is about, shown while it has the focus: for the cards whose title doesn't say it (e.g. DeArrow)
     */
    public SettingsItem setDescription(String description) {
        mDescription = description;
        return this;
    }

    /**
     * @return null when the card has none
     */
    public String getDescription() {
        return mDescription;
    }

    /**
     * The rows of the settings panel the card opens, as it shows them: for the settings search (see SettingsCatalog)
     */
    public SettingsItem setPanel(SettingsPanel panel) {
        mPanel = panel;
        return this;
    }

    /**
     * @return null when the card opens no settings panel (e.g. a section, or a panel loaded in the background)
     */
    public SettingsPanel getPanel() {
        return mPanel;
    }

    /**
     * The cards of the section the card opens: for the settings search (see SettingsCatalog)
     */
    public SettingsItem setCards(SettingsCardsPresenter.Cards cards) {
        mCards = cards;
        return this;
    }

    /**
     * @return null when the card opens no section
     */
    public SettingsCardsPresenter.Cards getCards() {
        return mCards;
    }

    /**
     * An address of the card (e.g. the site of the service), in the order shown
     */
    public SettingsItem addLink(String title, String url) {
        mLinks.add(new Link(title, url));
        return this;
    }

    /**
     * @return empty when the card has none
     */
    public List<Link> getLinks() {
        return Collections.unmodifiableList(mLinks);
    }
}
