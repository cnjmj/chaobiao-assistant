package com.example.datarecorder;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Locale;

public class RecordAdapter extends RecyclerView.Adapter<RecordAdapter.ViewHolder> {
    private List<Record> records; private String unit; private boolean isPrepaid;
    private OnItemClick clickListener; private OnItemLongClick longClickListener;
    public interface OnItemClick { void onClick(Record r, int pos); }
    public interface OnItemLongClick { void onLongClick(Record r, int pos); }
    public void setOnItemClick(OnItemClick l) { clickListener = l; }
    public void setOnItemLongClick(OnItemLongClick l) { longClickListener = l; }

    public RecordAdapter(List<Record> r, String u, boolean prepaid) { this.records = r; this.unit = u; this.isPrepaid = prepaid; }
    public void updateData(List<Record> r) { this.records = r; notifyDataSetChanged(); }

    @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new ViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_record, p, false));
    }

    @Override public void onBindViewHolder(@NonNull ViewHolder h, int pos) {
        Record r = records.get(pos);
        h.tvTime.setText(r.getFormattedTime());

        if (r.isRecharge()) {
            h.tvBalance.setText("+" + String.format(Locale.CHINA, "%.2f", r.getRechargeAmount()) + " 元");
            h.tvBalance.setTextColor(0xFFFF9800);
            h.tvUsageDiff.setText("充值");
            h.tvUsageDiff.setTextColor(0xFFFF9800);
            if (isPrepaid) h.tvCostDiff.setText("余额 " + r.getFormattedBalance() + " 元");
            else h.tvCostDiff.setText("");
            h.tvCostDiff.setTextColor(0xFF888888);
            h.tvIndex.setText("充");
        } else if (pos == records.size() - 1) {
            if (isPrepaid) h.tvBalance.setText(r.getFormattedBalance() + " 元");
            else h.tvBalance.setText(r.getFormattedBalance() + " " + unit);
            h.tvBalance.setTextColor(0xFF333333);
            h.tvUsageDiff.setText("首次记录"); h.tvCostDiff.setText("");
            h.tvUsageDiff.setTextColor(0xFF888888);
            h.tvIndex.setText(String.valueOf(records.size() - pos));
        } else {
            if (isPrepaid) h.tvBalance.setText(r.getFormattedBalance() + " 元");
            else h.tvBalance.setText(r.getFormattedBalance() + " " + unit);
            h.tvBalance.setTextColor(0xFF333333);

            boolean hasData = r.getCostDiff() > Record.EPSILON || r.getUsageDiff() > Record.EPSILON;
            if (hasData) {
                if (r.getUsageDiff() > Record.EPSILON) {
                    h.tvUsageDiff.setText(String.format(Locale.CHINA, "用量 %.2f %s", r.getUsageDiff(), unit));
                } else if (r.getCostDiff() > Record.EPSILON) {
                    h.tvUsageDiff.setText("用量 -- " + unit);
                } else h.tvUsageDiff.setText("");
                h.tvUsageDiff.setTextColor(0xFF009688);

                if (r.getCostDiff() > Record.EPSILON) {
                    h.tvCostDiff.setText(String.format(Locale.CHINA, "费用 %.2f 元", r.getCostDiff()));
                    h.tvCostDiff.setTextColor(0xFFFF7043);
                } else { h.tvCostDiff.setText(""); h.tvCostDiff.setTextColor(0xFF888888); }
            } else {
                h.tvUsageDiff.setText(""); h.tvUsageDiff.setTextColor(0xFF888888);
                h.tvCostDiff.setText(""); h.tvCostDiff.setTextColor(0xFF888888);
            }
            h.tvIndex.setText(String.valueOf(records.size() - pos));
        }

        if (r.getNote() != null && !r.getNote().isEmpty()) { h.tvNote.setText(r.getNote()); h.tvNote.setVisibility(View.VISIBLE); }
        else h.tvNote.setVisibility(View.GONE);

        h.itemView.setOnClickListener(v -> { if (clickListener != null) clickListener.onClick(r, pos); });
        h.itemView.setOnLongClickListener(v -> { if (longClickListener != null) { longClickListener.onLongClick(r, pos); return true; } return false; });
    }
    @Override public int getItemCount() { return records != null ? records.size() : 0; }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvIndex, tvBalance, tvTime, tvNote, tvUsageDiff, tvCostDiff;
        ViewHolder(View v) { super(v);
            tvIndex=v.findViewById(R.id.tv_index); tvBalance=v.findViewById(R.id.tv_balance);
            tvTime=v.findViewById(R.id.tv_time); tvNote=v.findViewById(R.id.tv_note);
            tvUsageDiff=v.findViewById(R.id.tv_usage_diff); tvCostDiff=v.findViewById(R.id.tv_cost_diff);
        }
    }
}