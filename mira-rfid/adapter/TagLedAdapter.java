package com.mira.rfid.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.uhf.R;
import com.rscja.deviceapi.entity.UHFTAGInfo;

import java.util.List;
import java.util.Objects;

/**
 * MIRA Spotlight Tag LED Adapter
 * 
 * محول عرض التاقات في قائمة الإضاءة (Spotlight)
 * يتيح اختيار القطع المحددة للإنذار/الإضاءة وعرض حالتها الحالية
 */
public class TagLedAdapter extends RecyclerView.Adapter<TagLedAdapter.ViewHolder> {

    private final List<UHFTAGInfo> mTagList;
    private OnTagLedClickListener mListener;

    public interface OnTagLedClickListener {
        boolean onItemClick(int position, boolean isChecked);
    }

    public void setTagLedClickListener(OnTagLedClickListener listener) {
        this.mListener = listener;
    }

    public TagLedAdapter(List<UHFTAGInfo> tagList) {
        this.mTagList = tagList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tag_led, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (mTagList == null || position < 0 || position >= mTagList.size()) return;

        UHFTAGInfo tagInfo = mTagList.get(position);

        String epc = tagInfo.getEPC();
        String title = tagInfo.getExtraData("TITLE");
        String serial = tagInfo.getExtraData("SERIAL");
        boolean isChecked = Objects.equals(tagInfo.getExtraData("CHECKED"), "1");
        boolean isLit = Objects.equals(tagInfo.getExtraData("STATE"), "1");

        // ضبط النصوص
        holder.tvEpc.setText(epc != null ? epc : "");
        
        if (title != null && !title.isEmpty()) {
            holder.tvTitle.setText("📦 " + title);
            holder.tvTitle.setVisibility(View.VISIBLE);
        } else {
            holder.tvTitle.setVisibility(View.GONE);
        }

        if (serial != null && !serial.isEmpty()) {
            holder.tvSerial.setText("🏷️ Serial: " + serial);
            holder.tvSerial.setVisibility(View.VISIBLE);
        } else {
            holder.tvSerial.setVisibility(View.GONE);
        }

        // حالة الإضاءة / الحساسية
        if (isLit) {
            holder.tvStatus.setText("💡 مضاءة الأن");
            holder.tvStatus.setVisibility(View.VISIBLE);
        } else {
            holder.tvStatus.setVisibility(View.GONE);
        }

        // ضبط الـ CheckBox بدون تفعيل المستمع أثناء التعيين الأولي
        holder.cbSelect.setOnCheckedChangeListener(null);
        holder.cbSelect.setChecked(isChecked);

        holder.cbSelect.setOnCheckedChangeListener((buttonView, checked) -> {
            int currentPos = holder.getAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION && currentPos < mTagList.size()) {
                if (mListener != null) {
                    boolean allowChange = mListener.onItemClick(currentPos, checked);
                    if (!allowChange) {
                        // إرجاع الحالة السابقة إذا رفض المستمع التغيير (مثلاً أثناء العمل الميداني)
                        buttonView.setChecked(!checked);
                        return;
                    }
                }
                mTagList.get(currentPos).setExtraData("CHECKED", checked ? "1" : "0");
            }
        });
    }

    @Override
    public int getItemCount() {
        return mTagList != null ? mTagList.size() : 0;
    }

    public void addTagInfo(UHFTAGInfo tagInfo) {
        if (tagInfo == null || mTagList == null) return;

        String epc = tagInfo.getEPC();
        if (epc == null || epc.isEmpty()) return;

        int index = -1;
        for (int i = 0; i < mTagList.size(); i++) {
            if (Objects.equals(mTagList.get(i).getEPC(), epc)) {
                index = i;
                break;
            }
        }

        if (index != -1) {
            mTagList.get(index).setExtraData("STATE", "1");
            notifyItemChanged(index);
        }
    }

    public void clear() {
        if (mTagList != null) {
            int size = mTagList.size();
            mTagList.clear();
            notifyItemRangeRemoved(0, size);
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvEpc, tvTitle, tvSerial, tvStatus;
        CheckBox cbSelect;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEpc = itemView.findViewById(R.id.tvEpc);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvSerial = itemView.findViewById(R.id.tvSerial);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            cbSelect = itemView.findViewById(R.id.cbSelect);
        }
    }
}
