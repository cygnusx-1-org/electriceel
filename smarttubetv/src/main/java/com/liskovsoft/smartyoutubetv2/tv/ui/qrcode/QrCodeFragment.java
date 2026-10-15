package com.liskovsoft.smartyoutubetv2.tv.ui.qrcode;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.QrCodePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.QrCodeView;
import com.liskovsoft.smartyoutubetv2.common.utils.QrCodeEncoder;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * The QR code in the middle of the screen, the address under it. The code is a fixed size, so it doesn't move with the address.
 */
public class QrCodeFragment extends Fragment implements QrCodeView {
    private QrCodePresenter mPresenter;
    private ImageView mCodeView;
    private TextView mUrlView;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPresenter = QrCodePresenter.instance(getContext());
        mPresenter.setView(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.qr_code, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mCodeView = view.findViewById(R.id.qr_code_image);
        mUrlView = view.findViewById(R.id.qr_code_url);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mPresenter.onViewInitialized();
    }

    @Override
    public void update(String url) {
        if (mCodeView == null) {
            return;
        }

        // Exactly the size of the view: a whole number of pixels per module, nothing scaled
        mCodeView.setImageBitmap(QrCodeEncoder.toBitmap(url, getResources().getDimensionPixelSize(R.dimen.qr_code_size)));
        mUrlView.setText(url);
    }
}
