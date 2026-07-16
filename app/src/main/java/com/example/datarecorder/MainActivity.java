package com.example.datarecorder;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements MeterAdapter.OnStartDragListener {
    private RecyclerView recyclerView; private MeterAdapter adapter;
    private DatabaseHelper dbHelper; private FloatingActionButton fabAdd;
    private TextView tvEmpty; private List<Meter> meterList = new ArrayList<>();
    private ItemTouchHelper itemTouchHelper;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        dbHelper = new DatabaseHelper(this);
        recyclerView = findViewById(R.id.recycler_view);
        fabAdd = findViewById(R.id.fab_add);
        tvEmpty = findViewById(R.id.tv_empty);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MeterAdapter(meterList, meter -> startActivity(new Intent(this, MeterDetailActivity.class).putExtra("meter_id", meter.getId())));
        adapter.setOnLongClickListener(meter -> {
            int recCount = dbHelper.getRecordCount(meter.getId());
            String msg = "确定删除表计 [" + meter.getName() + "] 吗？";
            if (recCount > 0) msg += "\n该表下有 " + recCount + " 条记录，将一并删除。";
            msg += "\n此操作不可撤销。";
            new AlertDialog.Builder(this).setTitle("删除表计").setMessage(msg)
                .setPositiveButton("删除", (d, w) -> { dbHelper.deleteMeter(meter.getId()); onResume(); })
                .setNegativeButton("取消", null).show();
        });
        adapter.setOnStartDragListener(this);
        recyclerView.setAdapter(adapter);

        // 拖拽排序
        ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override public boolean onMove(RecyclerView rv, RecyclerView.ViewHolder vh, RecyclerView.ViewHolder target) {
                int from = vh.getAdapterPosition();
                int to = target.getAdapterPosition();
                if (adapter.onItemMove(from, to)) return true;
                return false;
            }
            @Override public void onSwiped(RecyclerView.ViewHolder vh, int direction) {}
            @Override public void clearView(RecyclerView rv, RecyclerView.ViewHolder vh) {
                super.clearView(rv, vh);
                // 拖拽结束，持久化顺序
                List<Long> ids = new ArrayList<>();
                for (Meter m : adapter.getMeters()) ids.add(m.getId());
                dbHelper.updateSortOrders(ids);
            }
        };
        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(recyclerView);

        fabAdd.setOnClickListener(v -> startActivity(new Intent(this, AddMeterActivity.class)));
        findViewById(R.id.btn_batch).setOnClickListener(v -> startActivity(new Intent(this, BatchRecordActivity.class)));
        findViewById(R.id.btn_data).setOnClickListener(v -> startActivity(new Intent(this, DataManageActivity.class)));
    }

    @Override public void onStartDrag(MeterAdapter.ViewHolder holder) {
        itemTouchHelper.startDrag(holder);
    }

    @Override protected void onResume() {
        super.onResume();
        meterList = dbHelper.getAllMeters();
        adapter.updateData(meterList, dbHelper);
        tvEmpty.setVisibility(meterList.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(meterList.isEmpty() ? View.GONE : View.VISIBLE);
    }
}