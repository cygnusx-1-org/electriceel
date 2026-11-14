package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SettingsCardsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;

import java.util.ArrayList;
import java.util.List;

/**
 * The Content Filtering section of the settings, as cards on its own screen
 */
public class ContentFilteringSettingsPresenter extends BasePresenter<Void> implements SettingsCardsPresenter.Cards {
    private ContentFilteringSettingsPresenter(Context context) {
        super(context);
    }

    public static ContentFilteringSettingsPresenter instance(Context context) {
        return new ContentFilteringSettingsPresenter(context);
    }

    public void show() {
        SettingsCardsPresenter.instance(getContext()).show(this);
    }

    @Override
    public String getTitle(Context context) {
        return context.getString(R.string.settings_content_filtering);
    }

    @Override
    public List<SettingsItem> getItems(Context context) {
        List<SettingsItem> items = new ArrayList<>();
        items.add(new SettingsItem(context.getString(R.string.content_filtering_filtering),
                () -> FilteringSettingsPresenter.instance(context).show(), R.drawable.settings_content_filtering)
                .setDescription(context.getString(R.string.card_desc_filtering)));
        items.add(new SettingsItem(context.getString(R.string.hide_unwanted_content),
                () -> HideContentSettingsPresenter.instance(context).show(), R.drawable.settings_hide_content)
                .setDescription(context.getString(R.string.card_desc_hide_content)));
        items.add(new SettingsItem(context.getString(R.string.content_block_provider),
                () -> SponsorBlockSettingsPresenter.instance(context).show(), R.drawable.settings_block)
                .setDescription(context.getString(R.string.card_desc_sponsorblock))
                .addLink(context.getString(R.string.about_sponsorblock), context.getString(R.string.content_block_provider_url))
                .addLink(context.getString(R.string.content_block_status), context.getString(R.string.content_block_status_url)));
        items.add(new SettingsItem(context.getString(R.string.aislist_provider),
                () -> AiSListSettingsPresenter.instance(context).show(), R.drawable.settings_aislist)
                .setDescription(context.getString(R.string.card_desc_aislist))
                .addLink(context.getString(R.string.aislist_about), context.getString(R.string.aislist_provider_url)));

        return items;
    }
}
