package com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.VerticalGridView;
import androidx.preference.DialogPreference;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.Adapter;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.preference.LeanbackListPreferenceDialogFragment;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * A list put in order with the remote: OK picks an entry up (its handle turns into arrows), up and down move it, OK puts it down.
 * The preference is told of the new order at each move, so leaving the list with Back keeps it.
 */
public class ReorderListPreferenceDialogFragment extends LeanbackListPreferenceDialogFragment {
    // Rebinds an entry in place: a new view would take the focus away from the one moved
    private static final Object PAYLOAD_NUMBER = new Object();
    private boolean mIsTransparent;
    private VerticalGridView mGridView;

    public static ReorderListPreferenceDialogFragment newInstance(String key) {
        final Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);

        final ReorderListPreferenceDialogFragment fragment = new ReorderListPreferenceDialogFragment();
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public Adapter<ViewHolder> onCreateAdapter() {
        return new AdapterReorder();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        if (view != null) {
            if (mIsTransparent) {
                ViewUtil.enableTransparentDialog(getActivity(), view);
            }

            mGridView = view.findViewById(android.R.id.list);
        }

        return view;
    }

    public void enableTransparent(boolean enable) {
        mIsTransparent = enable;
    }

    private class AdapterReorder extends RecyclerView.Adapter<ViewHolder> implements ViewHolder.OnItemClickListener {
        // The entries by their place
        private final List<Integer> mOrder = new ArrayList<>();
        private int mPickedPosition = RecyclerView.NO_POSITION;

        private AdapterReorder() {
            int size = mEntries != null ? mEntries.length : 0;

            for (int i = 0; i < size; i++) {
                mOrder.add(i);
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.dialog_list_preference_item_reorder, parent, false);
            ViewHolder holder = new ViewHolder(view, this);
            holder.getContainer().setOnKeyListener((v, keyCode, event) -> onKey(holder, keyCode, event));
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.getTitleView().setText(String.format("%s %s", position + 1, mEntries[mOrder.get(position)]));
            ImageView handle = holder.itemView.findViewById(R.id.reorder_handle);
            handle.setImageResource(position == mPickedPosition ?
                    R.drawable.dialog_list_preference_item_reorder_move : R.drawable.dialog_list_preference_item_reorder_handle);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
            onBindViewHolder(holder, position);
        }

        @Override
        public int getItemCount() {
            return mOrder.size();
        }

        /**
         * Picks the entry up, or puts it down
         */
        @Override
        public void onItemClick(ViewHolder holder) {
            int position = holder.getAdapterPosition();

            if (position == RecyclerView.NO_POSITION) {
                return;
            }

            int pickedPosition = mPickedPosition;
            mPickedPosition = pickedPosition == position ? RecyclerView.NO_POSITION : position;

            notifyItemChanged(position, PAYLOAD_NUMBER);

            if (pickedPosition != RecyclerView.NO_POSITION && pickedPosition != position) {
                notifyItemChanged(pickedPosition, PAYLOAD_NUMBER);
            }
        }

        /**
         * The keys of the entry picked up: up and down move it. It keeps the focus while it's moved.
         */
        private boolean onKey(ViewHolder holder, int keyCode, KeyEvent event) {
            int position = holder.getAdapterPosition();

            if (position == RecyclerView.NO_POSITION || position != mPickedPosition) {
                return false;
            }

            boolean isDown = event.getAction() == KeyEvent.ACTION_DOWN;

            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (isDown) {
                        move(position, position - 1);
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (isDown) {
                        move(position, position + 1);
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    return true;
                default:
                    return false;
            }
        }

        private void move(int from, int to) {
            if (to < 0 || to >= mOrder.size()) {
                return;
            }

            mOrder.add(to, mOrder.remove(from));
            mPickedPosition = to;
            notifyItemMoved(from, to);
            // Their numbers
            notifyItemChanged(from, PAYLOAD_NUMBER);
            notifyItemChanged(to, PAYLOAD_NUMBER);

            if (mGridView != null) {
                mGridView.setSelectedPosition(to);
            }

            onReorder();
        }

        private void onReorder() {
            DialogPreference preference = getPreference();

            if (preference == null) {
                return;
            }

            List<String> values = new ArrayList<>();

            for (int index : mOrder) {
                values.add(mEntryValues[index].toString());
            }

            preference.callChangeListener(values);
        }
    }
}
