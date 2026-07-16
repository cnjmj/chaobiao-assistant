package com.example.datarecorder;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MeterAdapter extends RecyclerView.Adapter<MeterAdapter.ViewHolder> {
    private List<Meter> meters; private DatabaseHelper dbHelper; private OnClick listener; private OnLongClick longClickListener; private OnStartDragListener dragListener;
    public interface OnClick { void onClick(Meter m); }
    public interface OnLongClick { void onLongClick(Meter m); }
    public interface OnStartDragListener { void onStartDrag(ViewHolder holder); }
    public MeterAdapter(List<Meter> meters, OnClick l) { this.meters = meters; this.listener = l; }
    public void setOnLongClickListener(OnLongClick l) { this.longClickListener = l; }
    public void setOnStartDragListener(OnStartDragListener l) { this.dragListener = l; }
    public void updateData(List<Meter> m, DatabaseHelper db) { this.meters = m; this.dbHelper = db; notifyDataSetChanged(); }

    /** 拖拽移动项，返回true表示位置有变化 */
    public boolean onItemMove(int fromPos, int toPos) {
        if (fromPos < 0 || toPos < 0 || fromPos >= meters.size() || toPos >= meters.size()) return false;
        Collections.swap(meters, fromPos, toPos);
        notifyItemMoved(fromPos, toPos);
        return true;
    }

    public List<Meter> getMeters() { return meters; }

    @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new ViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_meter, p, false));
    }

    @Override public void onBindViewHolder(@NonNull ViewHolder h, int pos) {
        Meter m = meters.get(pos);
        h.tvName.setText(m.getName());
        h.tvType.setText(m.getTypeLabel() + " | " + m.getBillingModeLabel());
        h.tvNo.setText(m.getMeterNo() != null && !m.getMeterNo().isEmpty() ? "编号 " + m.getMeterNo() : "");
        if (dbHelper != null) {
            int cnt = dbHelper.getRecordCount(m.getId());
            h.tvCount.setText(cnt + " 条记录");
            List<Record> recs = dbHelper.getRecordsByMeter(m.getId());
            if (!recs.isEmpty()) {
                Record latest = recs.get(0);
                if (m.isPrepaid()) h.tvBalance.setText("余额: " + String.format(Locale.CHINA, "%.2f", latest.getBalance()) + " 元");
                else h.tvBalance.setText("读数: " + String.format(Locale.CHINA, "%.2f", latest.getBalance()) + " " + m.getUnit());
                h.tvTime.setText(latest.getFormattedTime());
            } else { h.tvBalance.setText("暂无记录"); h.tvTime.setText(""); }
        }
        h.itemView.setOnClickListener(v -> { if (listener != null) listener.onClick(m); });
        h.itemView.setOnLongClickListener(v -> { if (longClickListener != null) { longClickListener.onLongClick(m); return true; } return false; });
        // 拖拽手柄：触摸即启动拖拽
        if (h.dragHandle != null && dragListener != null) {
            h.dragHandle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    dragListener.onStartDrag(h);
                }
                return false;
            });
        }
    }
    @Override public int getItemCount() { return meters != null ? meters.size() : 0; }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvType, tvNo, tvCount, tvBalance, tvTime, dragHandle;
        ViewHolder(View v) { super(v);
            tvName=v.findViewById(R.id.tv_name); tvType=v.findViewById(R.id.tv_type);
            tvNo=v.findViewById(R.id.tv_meter_no); tvCount=v.findViewById(R.id.tv_count);
            tvBalance=v.findViewById(R.id.tv_last_balance); tvTime=v.findViewById(R.id.tv_last_time);
            dragHandle=v.findViewById(R.id.tv_drag_handle);
        }
    }
}