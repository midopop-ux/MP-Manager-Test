package io.github.abdurazaaqmohammed.ui.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.OverScroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.difflib.text.DiffRow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.abdurazaaqmohammed.util.text.TextDiffEngine;

/**
 * High-performance dual-pane diff view modeled after MT Manager's comparator:
 * renders only visible wrapped lines directly on Canvas without blank spacer
 * rows, synchronizes matching blocks smoothly, and draws cubic Bezier ribbons
 * in the center gutter connecting corresponding hunks.
 */
public class DualDiffCanvasView extends View {

    private static final int MAX_RENDERED_CHARS_PER_LINE = 4096;

    public static final class PaneLine {
        public final String text;
        public final int lineNumber;

        public PaneLine(String text, int lineNumber) {
            this.text = text != null ? text : "";
            this.lineNumber = lineNumber;
        }
    }

    private List<PaneLine> leftLines = Collections.emptyList();
    private List<PaneLine> rightLines = Collections.emptyList();
    private List<TextDiffEngine.DiffHunk> hunks = Collections.emptyList();

    private int[] leftLineStartRow = new int[]{0};
    private int[] rightLineStartRow = new int[]{0};
    private int[] hunkScrollStartRow = new int[]{0};
    private int[] hunkLeftStartRow = new int[0];
    private int[] hunkLeftEndRow = new int[0];
    private int[] hunkRightStartRow = new int[0];
    private int[] hunkRightEndRow = new int[0];

    private int totalTrackRows = 0;
    private int charsPerLine = 28;
    private float rowHeight;
    private float textBaselineOffset;
    private float numBaselineOffset;
    private float gutterWidth;
    private float numColWidth;
    private float panePaddingH;

    private float scrollY = 0f;
    private float maxScrollY = 0f;

    private int highlightedHunkIndex = -1;
    private String searchQuery = "";
    private boolean searchMatchCase = false;

    private final Paint bgPaint = new Paint();
    private final Paint gutterBgPaint = new Paint();
    private final Paint diffBlockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint diffBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activeDiffPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint equalShiftPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint searchHighlightPaint = new Paint();
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lineNumPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dividerPaint = new Paint();
    private final Path bezierPath = new Path();

    private final OverScroller scroller;
    private final GestureDetector gestureDetector;

    private boolean isDarkTheme;

    public DualDiffCanvasView(Context context) {
        this(context, null);
    }

    public DualDiffCanvasView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DualDiffCanvasView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        scroller = new OverScroller(context);
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                scroller.forceFinished(true);
                return true;
            }

            @Override
            public boolean onScroll(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
                scrollByY(distanceY);
                return true;
            }

            @Override
            public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
                scroller.fling(0, (int) scrollY, 0, (int) -velocityY, 0, 0, 0, (int) Math.max(0f, maxScrollY));
                postInvalidateOnAnimation();
                return true;
            }
        });

        initPaints(context);
    }

    private void initPaints(Context context) {
        int nightMask = context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        isDarkTheme = nightMask == android.content.res.Configuration.UI_MODE_NIGHT_YES;

        float textSizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 11.5f, context.getResources().getDisplayMetrics());
        float numSizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 9.5f, context.getResources().getDisplayMetrics());
        panePaddingH = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 4f, context.getResources().getDisplayMetrics());

        textPaint.setTypeface(Typeface.MONOSPACE);
        textPaint.setTextSize(textSizePx);

        lineNumPaint.setTypeface(Typeface.MONOSPACE);
        lineNumPaint.setTextSize(numSizePx);

        Paint.FontMetrics fm = textPaint.getFontMetrics();
        rowHeight = Math.max(18f, (float) Math.ceil((fm.descent - fm.ascent) * 1.25f));
        textBaselineOffset = (rowHeight - (fm.descent + fm.ascent)) * 0.5f;

        Paint.FontMetrics nfm = lineNumPaint.getFontMetrics();
        numBaselineOffset = (rowHeight - (nfm.descent + nfm.ascent)) * 0.5f;

        if (isDarkTheme) {
            bgPaint.setColor(0xFF1E1F22);
            gutterBgPaint.setColor(0xFF282A2E);
            diffBlockPaint.setColor(0xFF2A4B6C);
            diffBorderPaint.setColor(0xFF3E6D9C);
            activeDiffPaint.setColor(0xFF355F8A);
            equalShiftPaint.setColor(0xFF313338);
            searchHighlightPaint.setColor(0x66FFC107);
            textPaint.setColor(0xFFDCDFE4);
            lineNumPaint.setColor(0xFF8C929C);
            dividerPaint.setColor(0xFF383B40);
        } else {
            bgPaint.setColor(0xFFFAFAFA);
            gutterBgPaint.setColor(0xFFEDEDEF);
            diffBlockPaint.setColor(0xFFD6E6F5);
            diffBorderPaint.setColor(0xFF90B8DE);
            activeDiffPaint.setColor(0xFFBBD6F0);
            equalShiftPaint.setColor(0xFFE4E6EA);
            searchHighlightPaint.setColor(0x88FFEB3B);
            textPaint.setColor(0xFF1F2328);
            lineNumPaint.setColor(0xFF656D76);
            dividerPaint.setColor(0xFFD0D7DE);
        }

        diffBorderPaint.setStyle(Paint.Style.STROKE);
        diffBorderPaint.setStrokeWidth(1.5f);
        dividerPaint.setStrokeWidth(1f);
    }

    public void setData(List<PaneLine> left, List<PaneLine> right, List<TextDiffEngine.DiffHunk> diffHunks) {
        this.leftLines = left != null ? left : Collections.emptyList();
        this.rightLines = right != null ? right : Collections.emptyList();
        this.hunks = diffHunks != null ? diffHunks : Collections.emptyList();
        this.highlightedHunkIndex = -1;
        this.scrollY = 0f;
        scroller.forceFinished(true);
        rebuildLayoutTables();
        invalidate();
    }

    public void setSearchQuery(String query, boolean matchCase) {
        this.searchQuery = query != null ? query : "";
        this.searchMatchCase = matchCase;
        invalidate();
    }

    public void scrollToHunk(int hunkIndex) {
        if (hunkIndex < 0 || hunkIndex >= hunks.size()) return;
        this.highlightedHunkIndex = hunkIndex;
        float targetY = hunkScrollStartRow[hunkIndex] * rowHeight - Math.max(0f, getHeight() * 0.2f);
        setScrollYClamped(targetY);
    }

    public void scrollToLineNumber(int targetLineNumber) {
        if (leftLines.isEmpty() && rightLines.isEmpty()) return;
        int leftIdx = findLineIndexByNumber(leftLines, targetLineNumber);
        int leftRow = leftIdx < leftLineStartRow.length ? leftLineStartRow[leftIdx] : 0;
        // Map left visual row back to track row via hunkLeftStartRow.
        int hunkIdx = findHunkForLeftRow(leftRow);
        if (hunkIdx >= 0 && hunkIdx < hunks.size()) {
            int lStart = hunkLeftStartRow[hunkIdx];
            int lLen = Math.max(1, hunkLeftEndRow[hunkIdx] - lStart);
            int tLen = (hunkIdx + 1 < hunkScrollStartRow.length ? hunkScrollStartRow[hunkIdx + 1] : totalTrackRows)
                    - hunkScrollStartRow[hunkIdx];
            float frac = (float) (leftRow - lStart) / (float) lLen;
            float targetTrack = hunkScrollStartRow[hunkIdx] + frac * tLen;
            setScrollYClamped(targetTrack * rowHeight);
        }
    }

    private static int findLineIndexByNumber(List<PaneLine> lines, int targetNumber) {
        int lo = 0, hi = lines.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int num = lines.get(mid).lineNumber;
            if (num < targetNumber) lo = mid + 1;
            else if (num > targetNumber) hi = mid - 1;
            else return mid;
        }
        return Math.min(lo, Math.max(0, lines.size() - 1));
    }

    private void setScrollYClamped(float y) {
        scroller.forceFinished(true);
        scrollY = Math.max(0f, Math.min(maxScrollY, y));
        invalidate();
    }

    private void scrollByY(float dy) {
        scrollY = Math.max(0f, Math.min(maxScrollY, scrollY + dy));
        postInvalidateOnAnimation();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && w != oldw) {
            rebuildLayoutTables();
        } else {
            updateMaxScroll();
        }
    }

    private void rebuildLayoutTables() {
        int w = getWidth();
        if (w <= 0) return;

        int maxNum = Math.max(
                leftLines.isEmpty() ? 1 : leftLines.get(leftLines.size() - 1).lineNumber,
                rightLines.isEmpty() ? 1 : rightLines.get(rightLines.size() - 1).lineNumber);
        int digits = Math.max(2, String.valueOf(maxNum).length());
        float digitW = lineNumPaint.measureText("8");
        numColWidth = digitW * digits + panePaddingH * 2f;
        float centerBridgeMin = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 26f, getResources().getDisplayMetrics());
        gutterWidth = Math.min(w * 0.34f, numColWidth * 2f + centerBridgeMin);
        float paneWidth = Math.max(1f, (w - gutterWidth) * 0.5f);
        float charWidth = Math.max(1f, textPaint.measureText("M"));
        charsPerLine = Math.max(10, (int) Math.floor((paneWidth - panePaddingH * 2f) / charWidth));

        leftLineStartRow = buildWrapOffsets(leftLines, charsPerLine);
        rightLineStartRow = buildWrapOffsets(rightLines, charsPerLine);

        int hCount = hunks.size();
        hunkScrollStartRow = new int[hCount + 1];
        hunkLeftStartRow = new int[hCount];
        hunkLeftEndRow = new int[hCount];
        hunkRightStartRow = new int[hCount];
        hunkRightEndRow = new int[hCount];

        int trackAcc = 0;
        for (int i = 0; i < hCount; i++) {
            TextDiffEngine.DiffHunk hunk = hunks.get(i);
            int lStart = leftLineStartRow[Math.min(hunk.leftStart, leftLines.size())];
            int lEnd = leftLineStartRow[Math.min(hunk.leftEnd, leftLines.size())];
            int rStart = rightLineStartRow[Math.min(hunk.rightStart, rightLines.size())];
            int rEnd = rightLineStartRow[Math.min(hunk.rightEnd, rightLines.size())];
            hunkLeftStartRow[i] = lStart;
            hunkLeftEndRow[i] = lEnd;
            hunkRightStartRow[i] = rStart;
            hunkRightEndRow[i] = rEnd;
            hunkScrollStartRow[i] = trackAcc;
            int trackSpan = Math.max(1, Math.max(lEnd - lStart, rEnd - rStart));
            trackAcc += trackSpan;
        }
        hunkScrollStartRow[hCount] = trackAcc;
        totalTrackRows = trackAcc;
        updateMaxScroll();
    }

    private void updateMaxScroll() {
        float totalH = totalTrackRows * rowHeight;
        maxScrollY = Math.max(0f, totalH - getHeight() + rowHeight * 2f);
        if (scrollY > maxScrollY) scrollY = maxScrollY;
    }

    private static int[] buildWrapOffsets(List<PaneLine> lines, int wrapCols) {
        int n = lines.size();
        int[] offsets = new int[n + 1];
        int acc = 0;
        for (int i = 0; i < n; i++) {
            offsets[i] = acc;
            int len = Math.min(lines.get(i).text.length(), MAX_RENDERED_CHARS_PER_LINE);
            int rows = len == 0 ? 1 : (len + wrapCols - 1) / wrapCols;
            acc += rows;
        }
        offsets[n] = acc;
        return offsets;
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollY = Math.max(0f, Math.min(maxScrollY, scroller.getCurrY()));
            postInvalidateOnAnimation();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean handled = gestureDetector.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        return handled || super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        canvas.drawRect(0, 0, w, h, bgPaint);
        if (hunks.isEmpty()) return;

        float paneWidth = (w - gutterWidth) * 0.5f;
        float leftPaneEnd = paneWidth;
        float rightPaneStart = paneWidth + gutterWidth;

        // Map current scrollY -> (leftScrollY, rightScrollY) via active hunk anchor.
        float trackRow = scrollY / rowHeight;
        int anchorHunk = findHunkForTrackRow(trackRow);
        float leftScrollY = mapLeftScrollY(anchorHunk, trackRow);
        float rightScrollY = mapRightScrollY(anchorHunk, trackRow);

        // 1. Draw center gutter background
        canvas.drawRect(leftPaneEnd, 0, rightPaneStart, h, gutterBgPaint);

        // 2. Draw visible hunk backgrounds on left & right panes, plus Bezier connectors in gutter
        float gx1 = leftPaneEnd + numColWidth;
        float gx2 = rightPaneStart - numColWidth;
        float midX = (gx1 + gx2) * 0.5f;

        int firstVisibleHunk = Math.max(0, findHunkForTrackRow(Math.max(0f, trackRow - 4)) - 2);
        int lastVisibleHunk = Math.min(hunks.size() - 1, findHunkForTrackRow(trackRow + h / rowHeight + 4) + 2);

        for (int i = firstVisibleHunk; i <= lastVisibleHunk; i++) {
            TextDiffEngine.DiffHunk hunk = hunks.get(i);
            float ly1 = hunkLeftStartRow[i] * rowHeight - leftScrollY;
            float ly2 = hunkLeftEndRow[i] * rowHeight - leftScrollY;
            float ry1 = hunkRightStartRow[i] * rowHeight - rightScrollY;
            float ry2 = hunkRightEndRow[i] * rowHeight - rightScrollY;

            if (Math.max(ly2, ry2) < -rowHeight || Math.min(ly1, ry1) > h + rowHeight) continue;

            boolean isDiff = hunk.tag != DiffRow.Tag.EQUAL;
            Paint fillPaint = isDiff
                    ? (i == highlightedHunkIndex ? activeDiffPaint : diffBlockPaint)
                    : equalShiftPaint;

            if (isDiff) {
                if (ly2 > ly1) {
                    canvas.drawRect(0, ly1, leftPaneEnd + numColWidth, ly2, fillPaint);
                } else {
                    canvas.drawLine(0, ly1, leftPaneEnd + numColWidth, ly1, diffBorderPaint);
                }
                if (ry2 > ry1) {
                    canvas.drawRect(rightPaneStart - numColWidth, ry1, w, ry2, fillPaint);
                } else {
                    canvas.drawLine(rightPaneStart - numColWidth, ry1, w, ry1, diffBorderPaint);
                }
            }

            // Draw cubic Bezier connector in center bridge for diffs or vertically shifted equal blocks.
            if (isDiff || Math.abs(ly1 - ry1) > 1.0f || Math.abs(ly2 - ry2) > 1.0f) {
                bezierPath.reset();
                bezierPath.moveTo(gx1, ly1);
                bezierPath.cubicTo(midX, ly1, midX, ry1, gx2, ry1);
                bezierPath.lineTo(gx2, ry2);
                bezierPath.cubicTo(midX, ry2, midX, ly2, gx1, ly2);
                bezierPath.close();
                canvas.drawPath(bezierPath, fillPaint);

                if (isDiff) {
                    canvas.drawLine(gx1, ly1, gx1, ly1, diffBorderPaint);
                    bezierPath.reset();
                    bezierPath.moveTo(gx1, ly1);
                    bezierPath.cubicTo(midX, ly1, midX, ry1, gx2, ry1);
                    canvas.drawPath(bezierPath, diffBorderPaint);

                    bezierPath.reset();
                    bezierPath.moveTo(gx1, ly2);
                    bezierPath.cubicTo(midX, ly2, midX, ry2, gx2, ry2);
                    canvas.drawPath(bezierPath, diffBorderPaint);
                }
            }
        }

        // 3. Draw Left Pane text & left line numbers
        canvas.save();
        canvas.clipRect(0, 0, leftPaneEnd, h);
        drawPaneLines(canvas, leftLines, leftLineStartRow, leftScrollY, panePaddingH, h, true);
        canvas.restore();

        drawPaneNumbers(canvas, leftLines, leftLineStartRow, leftScrollY, leftPaneEnd + numColWidth - panePaddingH, h, true);

        // 4. Draw Right Pane text & right line numbers
        canvas.save();
        canvas.clipRect(rightPaneStart, 0, w, h);
        drawPaneLines(canvas, rightLines, rightLineStartRow, rightScrollY, rightPaneStart + panePaddingH, h, false);
        canvas.restore();

        drawPaneNumbers(canvas, rightLines, rightLineStartRow, rightScrollY, rightPaneStart - numColWidth + panePaddingH, h, false);

        // Subtle vertical dividers flanking the text panes
        canvas.drawLine(leftPaneEnd, 0, leftPaneEnd, h, dividerPaint);
        canvas.drawLine(rightPaneStart, 0, rightPaneStart, h, dividerPaint);
    }

    private void drawPaneLines(Canvas canvas, List<PaneLine> lines, int[] startRows,
                               float paneScrollY, float textX, int viewHeight, boolean isLeft) {
        if (lines.isEmpty()) return;
        int totalRows = startRows[lines.size()];
        int firstRow = Math.max(0, (int) Math.floor(paneScrollY / rowHeight));
        int lastRow = Math.min(totalRows - 1, (int) Math.ceil((paneScrollY + viewHeight) / rowHeight));
        if (firstRow > lastRow) return;

        int lineIdx = findLineForVisualRow(startRows, firstRow);
        String queryLower = searchQuery.isEmpty() ? "" : (searchMatchCase ? searchQuery : searchQuery.toLowerCase());
        float charW = textPaint.measureText("M");

        for (int row = firstRow; row <= lastRow; row++) {
            while (lineIdx + 1 < lines.size() && startRows[lineIdx + 1] <= row) {
                lineIdx++;
            }
            PaneLine line = lines.get(lineIdx);
            int rowInLine = row - startRows[lineIdx];
            int sliceStart = rowInLine * charsPerLine;
            int maxLen = Math.min(line.text.length(), MAX_RENDERED_CHARS_PER_LINE);
            if (sliceStart >= maxLen) continue;
            int sliceEnd = Math.min(maxLen, sliceStart + charsPerLine);

            float y = row * rowHeight - paneScrollY;
            if (!queryLower.isEmpty()) {
                String slice = line.text.substring(sliceStart, sliceEnd);
                String hay = searchMatchCase ? slice : slice.toLowerCase();
                int idx = hay.indexOf(queryLower);
                while (idx >= 0) {
                    float hx1 = textX + idx * charW;
                    float hx2 = hx1 + queryLower.length() * charW;
                    canvas.drawRect(hx1, y, hx2, y + rowHeight, searchHighlightPaint);
                    idx = hay.indexOf(queryLower, idx + queryLower.length());
                }
            }
            canvas.drawText(line.text, sliceStart, sliceEnd, textX, y + textBaselineOffset, textPaint);
        }
    }

    private void drawPaneNumbers(Canvas canvas, List<PaneLine> lines, int[] startRows,
                                 float paneScrollY, float anchorX, int viewHeight, boolean rightAlign) {
        if (lines.isEmpty()) return;
        int totalRows = startRows[lines.size()];
        int firstRow = Math.max(0, (int) Math.floor(paneScrollY / rowHeight));
        int lastRow = Math.min(totalRows - 1, (int) Math.ceil((paneScrollY + viewHeight) / rowHeight));
        if (firstRow > lastRow) return;

        lineNumPaint.setTextAlign(rightAlign ? Paint.Align.RIGHT : Paint.Align.LEFT);
        int lineIdx = findLineForVisualRow(startRows, firstRow);
        while (lineIdx < lines.size()) {
            int row = startRows[lineIdx];
            if (row > lastRow) break;
            if (row >= firstRow) {
                float y = row * rowHeight - paneScrollY;
                canvas.drawText(String.valueOf(lines.get(lineIdx).lineNumber),
                        anchorX, y + numBaselineOffset, lineNumPaint);
            }
            lineIdx++;
        }
    }

    private int findHunkForTrackRow(float trackRow) {
        int hCount = hunks.size();
        if (hCount == 0) return 0;
        int target = (int) Math.floor(trackRow);
        int lo = 0, hi = hCount - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (hunkScrollStartRow[mid + 1] <= target) lo = mid + 1;
            else if (hunkScrollStartRow[mid] > target) hi = mid - 1;
            else return mid;
        }
        return Math.max(0, Math.min(hCount - 1, lo));
    }

    private int findHunkForLeftRow(int leftRow) {
        int hCount = hunks.size();
        if (hCount == 0) return 0;
        int lo = 0, hi = hCount - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (hunkLeftEndRow[mid] <= leftRow) lo = mid + 1;
            else if (hunkLeftStartRow[mid] > leftRow) hi = mid - 1;
            else return mid;
        }
        return Math.max(0, Math.min(hCount - 1, lo));
    }

    private static int findLineForVisualRow(int[] startRows, int row) {
        int idx = Arrays.binarySearch(startRows, row);
        if (idx >= 0) {
            while (idx + 1 < startRows.length - 1 && startRows[idx + 1] == row) idx++;
            return Math.min(idx, startRows.length - 2);
        }
        return Math.max(0, Math.min(startRows.length - 2, -idx - 2));
    }

    private float mapLeftScrollY(int hunkIdx, float trackRow) {
        if (hunkIdx < 0 || hunkIdx >= hunks.size()) return 0f;
        int tStart = hunkScrollStartRow[hunkIdx];
        int tSpan = Math.max(1, hunkScrollStartRow[hunkIdx + 1] - tStart);
        float frac = Math.max(0f, Math.min(1f, (trackRow - tStart) / (float) tSpan));
        int lStart = hunkLeftStartRow[hunkIdx];
        int lSpan = hunkLeftEndRow[hunkIdx] - lStart;
        return (lStart + frac * lSpan) * rowHeight;
    }

    private float mapRightScrollY(int hunkIdx, float trackRow) {
        if (hunkIdx < 0 || hunkIdx >= hunks.size()) return 0f;
        int tStart = hunkScrollStartRow[hunkIdx];
        int tSpan = Math.max(1, hunkScrollStartRow[hunkIdx + 1] - tStart);
        float frac = Math.max(0f, Math.min(1f, (trackRow - tStart) / (float) tSpan));
        int rStart = hunkRightStartRow[hunkIdx];
        int rSpan = hunkRightEndRow[hunkIdx] - rStart;
        return (rStart + frac * rSpan) * rowHeight;
    }
}
