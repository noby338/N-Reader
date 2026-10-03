package com.noby.nreader;

import android.content.Context;
import android.graphics.Canvas;
import android.text.Layout;
import android.view.View;

public class BookPageView extends View {
    private Layout layout;
    private int startLine = 0;
    private int endLine = 0;

    public BookPageView(Context context) {
        super(context);
        setFocusable(false);
    }

    public void setPage(Layout layout, int startLine, int endLine) {
        this.layout = layout;
        this.startLine = startLine;
        this.endLine = endLine;
        invalidate();
    }

    public void clear() {
        this.layout = null;
        this.startLine = 0;
        this.endLine = 0;
        invalidate();
    }

    public int getStartLine() {
        return startLine;
    }

    public int getEndLine() {
        return endLine;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (layout == null || endLine <= startLine || startLine >= layout.getLineCount()) {
            return;
        }

        int safeEnd = Math.min(layout.getLineCount(), endLine);
        int top = layout.getLineTop(startLine);
        int bottom = layout.getLineBottom(safeEnd - 1);

        canvas.save();
        canvas.clipRect(0, 0, getWidth(), bottom - top);
        canvas.translate(0, -top);
        layout.draw(canvas);
        canvas.restore();
    }
}
