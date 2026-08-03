package com.mira.rfid.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.uhf.R;
import com.rscja.deviceapi.entity.UHFTAGInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * MIRA RFID EPC List Adapter
 * 
 * المحول الرئيسي لعرض بيانات التاقات المقروءة (EPC, Count, RSSI, TID)
 * مخصص ومحسن للأداء العالي مع القراءة السريعة بالجملة
 */
public class EPCListAdapter extends BaseAdapter {

    private final Context context;
    private final List<UHFTAGInfo> list;
    private final LayoutInflater inflater;

    public EPCListAdapter(Context context, List<UHFTAGInfo> list) {
        this.context = context;
        this.list = list != null ? list : new ArrayList<>();
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return list.size();
    }

    @Override
    public Object getItem(int position) {
        if (position >= 0 && position < list.size()) {
            return list.get(position);
        }
        return null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_epc_list, parent, false);
            holder = new ViewHolder();
            holder.tvIndex = convertView.findViewById(R.id.tvIndex);
            holder.tvEPC = convertView.findViewById(R.id.tvEPC);
            holder.tvCount = convertView.findViewById(R.id.tvCount);
            holder.tvRssi = convertView.findViewById(R.id.tvRssi);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        UHFTAGInfo tagInfo = list.get(position);
        if (tagInfo != null) {
            if (holder.tvIndex != null) {
                holder.tvIndex.setText(String.valueOf(position + 1));
            }
            if (holder.tvEPC != null) {
                holder.tvEPC.setText(tagInfo.getEPC() != null ? tagInfo.getEPC() : "");
            }
            if (holder.tvCount != null) {
                holder.tvCount.setText(String.valueOf(tagInfo.getCount()));
            }
            if (holder.tvRssi != null) {
                holder.tvRssi.setText(tagInfo.getRssi() != null ? tagInfo.getRssi() : "");
            }
        }

        return convertView;
    }

    public void clear() {
        list.clear();
        notifyDataSetChanged();
    }

    static class ViewHolder {
        TextView tvIndex;
        TextView tvEPC;
        TextView tvCount;
        TextView tvRssi;
    }
}
