package com.dex7er.ctvplayer;

import android.view.View;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView;

public class HeaderFooterAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private final RecyclerView.Adapter<RecyclerView.ViewHolder> adapter;
    private final View headerView;
    private final View footerView;

    public HeaderFooterAdapter(RecyclerView.Adapter<RecyclerView.ViewHolder> adapter, View headerView, View footerView) {
        this.adapter = adapter;
        this.headerView = headerView;
        this.footerView = footerView;
    }

    @Override
    public int getItemViewType(int position) {
        if (headerView != null && position == 0) return TYPE_HEADER;
        return TYPE_ITEM;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            return new RecyclerView.ViewHolder(headerView) {};
        }
        return adapter.onCreateViewHolder(parent, viewType);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == TYPE_ITEM) {
            adapter.onBindViewHolder(holder, position - (headerView != null ? 1 : 0));
        }
    }

    @Override
    public int getItemCount() {
        int count = adapter.getItemCount();
        if (headerView != null) count++;
        if (footerView != null) count++;
        return count;
    }
}