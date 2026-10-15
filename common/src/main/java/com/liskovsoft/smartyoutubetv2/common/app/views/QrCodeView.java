package com.liskovsoft.smartyoutubetv2.common.app.views;

/**
 * The QR code of an address on the whole screen, the address written under it (see QrCodePresenter)
 */
public interface QrCodeView {
    void update(String url);
}
