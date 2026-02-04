package net.micode.notes.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import net.micode.notes.R;
import net.micode.notes.tool.ResourceParser;

import java.util.List;

public class NoteColorAdapter extends RecyclerView.Adapter<NoteColorAdapter.ViewHolder> {

    public interface OnColorClickListener {
        void onColorClick(int colorId);
    }

    private List<Integer> mColorIds;
    private int mSelectedColorId;
    private OnColorClickListener mListener;

    public NoteColorAdapter(List<Integer> colorIds, int selectedColorId, OnColorClickListener listener) {
        mColorIds = colorIds;
        mSelectedColorId = selectedColorId;
        mListener = listener;
    }

    public void setSelectedColor(int colorId) {
        mSelectedColorId = colorId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.note_color_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        int colorId = mColorIds.get(position);
        
        if (colorId == ResourceParser.CUSTOM_COLOR_BUTTON_ID) {
            holder.colorView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            int padding = (int) (12 * holder.itemView.getContext().getResources().getDisplayMetrics().density);
            holder.colorView.setPadding(padding, padding, padding, padding);
            holder.colorView.setImageResource(R.drawable.ic_palette);
            // Apply a dark tint to ensure visibility
            holder.colorView.setColorFilter(android.graphics.Color.DKGRAY, android.graphics.PorterDuff.Mode.SRC_IN);
            // Optional: Set a background for the icon
            holder.colorView.setBackgroundResource(R.drawable.bg_color_btn_mask); 
        } else if (colorId == ResourceParser.WALLPAPER_BUTTON_ID) {
            holder.colorView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            int padding = (int) (12 * holder.itemView.getContext().getResources().getDisplayMetrics().density);
            holder.colorView.setPadding(padding, padding, padding, padding);
            holder.colorView.setImageResource(R.drawable.ic_image);
            // Apply a dark tint to ensure visibility
            holder.colorView.setColorFilter(android.graphics.Color.DKGRAY, android.graphics.PorterDuff.Mode.SRC_IN);
            // Optional: Set a background for the icon
            holder.colorView.setBackgroundResource(R.drawable.bg_color_btn_mask);
        } else {
            holder.colorView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            holder.colorView.setPadding(0, 0, 0, 0);
            // 使用ResourceParser获取背景资源
            int bgRes = ResourceParser.NoteBgResources.getNoteBgResource(colorId);
            holder.colorView.setImageResource(bgRes);
            holder.colorView.setBackground(null); // Clear background if reused

            if (colorId >= ResourceParser.MIDNIGHT_BLACK || colorId < 0) {
                int color = ResourceParser.getNoteBgColor(holder.itemView.getContext(), colorId);
                holder.colorView.setColorFilter(color, android.graphics.PorterDuff.Mode.MULTIPLY);
            } else {
                holder.colorView.clearColorFilter();
            }
        }

        if (colorId == mSelectedColorId && colorId != ResourceParser.CUSTOM_COLOR_BUTTON_ID) {
            holder.checkView.setVisibility(View.VISIBLE);
        } else {
            holder.checkView.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onColorClick(colorId);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mColorIds.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView colorView;
        ImageView checkView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            colorView = itemView.findViewById(R.id.color_view);
            checkView = itemView.findViewById(R.id.check_view);
        }
    }
}
