package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.startgraph;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.common.diagnostics.StartGraphText;
import com.liskovsoft.smartyoutubetv2.common.diagnostics.VideoStart;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Where the time of the start of a video went (see VideoStarts), as a waterfall: a row per stage, labelled, its bar on a time axis
 * from the user's action, colored by the kind of work (see VideoStart#getCategory), with a legend of the colors below. Gone without
 * a start.
 */
public class StartGraphView extends View {
    /**
     * Past it the rest is counted in a row: the screen is only so high
     */
    private static final int MAX_ROWS = 24;
    private static final float TITLE_SP = 12;
    private static final float TEXT_SP = 11;
    private static final float AXIS_SP = 10;
    private static final float PADDING_SP = 8;
    private static final float GAP_SP = 6;
    private static final float INDENT_SP = 8;
    private static final float ROW_SP = 15;
    private static final float BAR_HEIGHT_SP = 8;
    private static final float BAR_RADIUS_SP = 2;
    private static final float MIN_BAR_SP = 2;
    private static final float BAR_AREA_SP = 220;
    private static final float MAX_LABEL_SP = 120;
    private static final float MAX_DETAIL_SP = 90;
    private static final float SWATCH_SP = 8;
    /**
     * Between the seconds marked on the axis, at least
     */
    private static final float MIN_TICK_SP = 34;
    private final TextPaint mTitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mSecondaryPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mAxisPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGridPaint = new Paint();
    private final Paint mBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mMarkPaint = new Paint();
    private final RectF mRect = new RectF();
    private final Map<VideoStart.Category, Integer> mColors = new EnumMap<>(VideoStart.Category.class);
    private final Map<VideoStart.Category, String> mLegendLabels = new EnumMap<>(VideoStart.Category.class);
    private final List<VideoStart.Stage> mRows = new ArrayList<>();
    private final List<VideoStart.Category> mLegend = new ArrayList<>();
    private final float mSp;
    @Nullable
    private VideoStart mStart;
    private String mTitle;
    private String mMore;
    private long mSpanMs;
    private float mLabelWidth;
    private float mValueWidth;
    private float mDetailWidth;
    private int mLegendLines;

    public StartGraphView(Context context) {
        this(context, null);
    }

    public StartGraphView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StartGraphView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        mSp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1, getResources().getDisplayMetrics());

        int text = ContextCompat.getColor(context, R.color.start_graph_text);
        int secondary = ContextCompat.getColor(context, R.color.start_graph_text_secondary);
        mTitlePaint.setColor(text);
        mTitlePaint.setTextSize(TITLE_SP * mSp);
        mTitlePaint.setTypeface(Typeface.DEFAULT_BOLD);
        mTextPaint.setColor(text);
        mTextPaint.setTextSize(TEXT_SP * mSp);
        mSecondaryPaint.setColor(secondary);
        mSecondaryPaint.setTextSize(TEXT_SP * mSp);
        mAxisPaint.setColor(secondary);
        mAxisPaint.setTextSize(AXIS_SP * mSp);
        mGridPaint.setColor(ContextCompat.getColor(context, R.color.start_graph_grid));
        mGridPaint.setStrokeWidth(Math.max(1, mSp));
        mMarkPaint.setColor(text);
        mMarkPaint.setStrokeWidth(2 * mSp);

        putCategory(VideoStart.Category.APP, R.color.start_graph_app, R.string.start_graph_legend_app);
        putCategory(VideoStart.Category.WAIT, R.color.start_graph_wait, R.string.start_graph_legend_wait);
        putCategory(VideoStart.Category.FORMATS, R.color.start_graph_formats, R.string.start_graph_legend_formats);
        putCategory(VideoStart.Category.NETWORK, R.color.start_graph_network, R.string.start_graph_legend_network);
        putCategory(VideoStart.Category.POTOKEN, R.color.start_graph_potoken, R.string.start_graph_legend_potoken);
        putCategory(VideoStart.Category.PLAYER_JS, R.color.start_graph_player_js, R.string.start_graph_legend_player_js);
        putCategory(VideoStart.Category.BUFFER, R.color.start_graph_buffer, R.string.start_graph_legend_buffer);
        putCategory(VideoStart.Category.DECODER, R.color.start_graph_decoder, R.string.start_graph_legend_decoder);
        putCategory(VideoStart.Category.MODE_SWITCH, R.color.start_graph_mode_switch, R.string.start_graph_legend_mode_switch);
        mColors.put(VideoStart.Category.MARK, text);
    }

    /**
     * Null hides it
     */
    public void setStart(@Nullable VideoStart start) {
        mStart = start;
        mRows.clear();
        mLegend.clear();

        if (start == null) {
            setVisibility(View.GONE);
            return;
        }

        List<VideoStart.Stage> stages = start.stages;
        mRows.addAll(stages.subList(0, Math.min(MAX_ROWS, stages.size())));
        int hidden = stages.size() - mRows.size();
        mMore = hidden > 0 ? getResources().getQuantityString(R.plurals.start_graph_more, hidden, hidden) : null;
        mSpanMs = Math.max(1, start.totalMs);
        mLabelWidth = 0;
        mDetailWidth = 0;

        for (VideoStart.Stage stage : stages) {
            mSpanMs = Math.max(mSpanMs, stage.endMs);
        }

        for (VideoStart.Stage stage : mRows) {
            mLabelWidth = Math.max(mLabelWidth, stage.depth * INDENT_SP * mSp + mTextPaint.measureText(stage.name));

            if (stage.detail != null) {
                mDetailWidth = Math.max(mDetailWidth, mSecondaryPaint.measureText(stage.detail));
            }

            VideoStart.Category category = stage.getCategory();

            if (category != VideoStart.Category.MARK && !mLegend.contains(category)) {
                mLegend.add(category);
            }
        }

        mLabelWidth = Math.min(mLabelWidth, MAX_LABEL_SP * mSp);
        mDetailWidth = Math.min(mDetailWidth, MAX_DETAIL_SP * mSp);
        mValueWidth = mTextPaint.measureText("00000");
        int titleId = start.isPlayed() ? R.string.start_graph_title
                : VideoStart.OUTCOME_WAITING.equals(start.outcome) ? R.string.start_graph_title_waiting : R.string.start_graph_title_never;
        mTitle = getResources().getString(titleId, start.totalMs, start.describe());
        mLegendLines = countLegendLines(getContentWidth());

        setVisibility(View.VISIBLE);
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mStart == null) {
            setMeasuredDimension(0, 0);
            return;
        }

        float rows = mRows.size() + (mMore != null ? 1 : 0);
        float height = 2 * PADDING_SP * mSp + ROW_SP * mSp * (2 + rows + mLegendLines);

        setMeasuredDimension((int) Math.ceil(getContentWidth() + 2 * PADDING_SP * mSp), (int) Math.ceil(height));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mStart == null) {
            return;
        }

        float padding = PADDING_SP * mSp;
        float rowHeight = ROW_SP * mSp;
        float left = padding;
        float barLeft = left + mLabelWidth + GAP_SP * mSp;
        float barWidth = BAR_AREA_SP * mSp;
        float valueRight = barLeft + barWidth + GAP_SP * mSp + mValueWidth;
        float top = padding;

        // The title, then the axis
        canvas.drawText(TextUtils.ellipsize(mTitle, mTitlePaint, getContentWidth(), TextUtils.TruncateAt.END).toString(), left,
                baseline(top, rowHeight, mTitlePaint), mTitlePaint);
        top += rowHeight;

        float rowsTop = top + rowHeight;
        float rowsBottom = rowsTop + rowHeight * mRows.size();
        long tickMs = StartGraphText.getTickStepMs(mSpanMs, (int) BAR_AREA_SP, (int) MIN_TICK_SP);

        for (long ms = 0; ms <= mSpanMs; ms += tickMs) {
            float x = barLeft + barWidth * ms / mSpanMs;
            canvas.drawLine(x, rowsTop, x, rowsBottom, mGridPaint);
            canvas.drawText(StartGraphText.formatTick(ms), x, baseline(top, rowHeight, mAxisPaint), mAxisPaint);
        }

        top = rowsTop;

        for (VideoStart.Stage stage : mRows) {
            drawRow(canvas, stage, left, top, barLeft, barWidth, valueRight);
            top += rowHeight;
        }

        if (mMore != null) {
            canvas.drawText(mMore, left, baseline(top, rowHeight, mSecondaryPaint), mSecondaryPaint);
            top += rowHeight;
        }

        drawLegend(canvas, left, top);
    }

    private void drawRow(Canvas canvas, VideoStart.Stage stage, float left, float top, float barLeft, float barWidth, float valueRight) {
        float rowHeight = ROW_SP * mSp;
        float indent = stage.depth * INDENT_SP * mSp;
        float textBaseline = baseline(top, rowHeight, mTextPaint);
        // The stages in another are in the secondary color: the ones of the start stand out
        TextPaint labelPaint = stage.depth == 0 ? mTextPaint : mSecondaryPaint;
        CharSequence label = TextUtils.ellipsize(stage.name, labelPaint, Math.max(0, mLabelWidth - indent), TextUtils.TruncateAt.END);
        canvas.drawText(label.toString(), left + indent, textBaseline, labelPaint);

        boolean isMark = stage.getCategory() == VideoStart.Category.MARK;
        float x0 = barLeft + barWidth * stage.startMs / mSpanMs;

        if (isMark) {
            canvas.drawLine(x0, top + 2 * mSp, x0, top + rowHeight - 2 * mSp, mMarkPaint);
        } else {
            float x1 = Math.max(x0 + MIN_BAR_SP * mSp, barLeft + barWidth * stage.endMs / mSpanMs);
            float barTop = top + (rowHeight - BAR_HEIGHT_SP * mSp) / 2;
            mRect.set(x0, barTop, x1, barTop + BAR_HEIGHT_SP * mSp);
            mBarPaint.setColor(mColors.get(stage.getCategory()));
            canvas.drawRoundRect(mRect, BAR_RADIUS_SP * mSp, BAR_RADIUS_SP * mSp, mBarPaint);
        }

        String value = String.valueOf(isMark ? stage.startMs : stage.getDurationMs());
        canvas.drawText(value, valueRight - mTextPaint.measureText(value), textBaseline, mTextPaint);

        if (stage.detail != null) {
            CharSequence detail = TextUtils.ellipsize(stage.detail, mSecondaryPaint, mDetailWidth, TextUtils.TruncateAt.END);
            canvas.drawText(detail.toString(), valueRight + GAP_SP * mSp, textBaseline, mSecondaryPaint);
        }
    }

    /**
     * The colors of the kinds of stage shown, wrapped to the graph's width
     */
    private void drawLegend(Canvas canvas, float left, float top) {
        float rowHeight = ROW_SP * mSp;
        float swatch = SWATCH_SP * mSp;
        float x = left;
        float maxX = left + getContentWidth();

        for (VideoStart.Category category : mLegend) {
            String label = mLegendLabels.get(category);
            float width = swatch + GAP_SP * mSp / 2 + mSecondaryPaint.measureText(label) + GAP_SP * mSp * 2;

            if (x > left && x + width > maxX) {
                x = left;
                top += rowHeight;
            }

            float swatchTop = top + (rowHeight - swatch) / 2;
            mRect.set(x, swatchTop, x + swatch, swatchTop + swatch);
            mBarPaint.setColor(mColors.get(category));
            canvas.drawRoundRect(mRect, BAR_RADIUS_SP * mSp, BAR_RADIUS_SP * mSp, mBarPaint);
            canvas.drawText(label, x + swatch + GAP_SP * mSp / 2, baseline(top, rowHeight, mSecondaryPaint), mSecondaryPaint);
            x += width;
        }
    }

    private int countLegendLines(float maxWidth) {
        if (mLegend.isEmpty()) {
            return 0;
        }

        int lines = 1;
        float x = 0;
        float swatch = SWATCH_SP * mSp;

        for (VideoStart.Category category : mLegend) {
            float width = swatch + GAP_SP * mSp / 2 + mSecondaryPaint.measureText(mLegendLabels.get(category)) + GAP_SP * mSp * 2;

            if (x > 0 && x + width > maxWidth) {
                lines++;
                x = 0;
            }

            x += width;
        }

        return lines;
    }

    private float getContentWidth() {
        float width = mLabelWidth + GAP_SP * mSp + BAR_AREA_SP * mSp + GAP_SP * mSp + mValueWidth;

        return mDetailWidth > 0 ? width + GAP_SP * mSp + mDetailWidth : width;
    }

    /**
     * Of text centered in the row
     */
    private static float baseline(float top, float rowHeight, Paint paint) {
        Paint.FontMetrics metrics = paint.getFontMetrics();

        return top + (rowHeight - metrics.descent - metrics.ascent) / 2;
    }

    private void putCategory(VideoStart.Category category, int colorId, int labelId) {
        mColors.put(category, ContextCompat.getColor(getContext(), colorId));
        mLegendLabels.put(category, getResources().getString(labelId));
    }
}
