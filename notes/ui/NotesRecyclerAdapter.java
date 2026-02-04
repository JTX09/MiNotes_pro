package net.micode.notes.ui;

import android.content.Context;
import android.database.Cursor;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.ResourceParser;

public class NotesRecyclerAdapter extends RecyclerView.Adapter<NotesRecyclerAdapter.NoteViewHolder> {

    private Context mContext;
    private Cursor mCursor;
    private OnNoteItemClickListener mListener;
    private boolean mChoiceMode;

    public interface OnNoteItemClickListener {
        void onNoteClick(int position, long noteId);
        boolean onNoteLongClick(int position, long noteId);
    }

    public NotesRecyclerAdapter(Context context) {
        mContext = context;
    }

    public void setOnNoteItemClickListener(OnNoteItemClickListener listener) {
        mListener = listener;
    }

    public void swapCursor(Cursor newCursor) {
        if (mCursor == newCursor) return;
        if (mCursor != null) {
            mCursor.close();
        }
        mCursor = newCursor;
        notifyDataSetChanged();
    }
    
    public Cursor getCursor() {
        return mCursor;
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.note_item, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        if (mCursor == null || !mCursor.moveToPosition(position)) {
            return;
        }
        NoteItemData itemData = new NoteItemData(mContext, mCursor);
        holder.bind(itemData, mChoiceMode, false); // Checked logic omitted for now
        
        holder.itemView.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onNoteClick(position, itemData.getId());
            }
        });
        
        holder.itemView.setOnLongClickListener(v -> {
            if (mListener != null) {
                return mListener.onNoteLongClick(position, itemData.getId());
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return mCursor == null ? 0 : mCursor.getCount();
    }

    class NoteViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView title, time, name;
        ImageView typeIcon, lockIcon, alertIcon;
        CheckBox checkBox;

        public NoteViewHolder(View itemView) {
            super(itemView);
            // Since root is CardView
            cardView = (CardView) itemView;
            title = itemView.findViewById(R.id.tv_title);
            time = itemView.findViewById(R.id.tv_time);
            name = itemView.findViewById(R.id.tv_name);
            checkBox = itemView.findViewById(android.R.id.checkbox);
            typeIcon = itemView.findViewById(R.id.iv_type_icon);
            lockIcon = itemView.findViewById(R.id.iv_lock_icon);
            alertIcon = itemView.findViewById(R.id.iv_alert_icon);
        }

        public void bind(NoteItemData data, boolean choiceMode, boolean checked) {
            if (choiceMode && (data.getType() == Notes.TYPE_NOTE || data.getType() == Notes.TYPE_TEMPLATE)) {
                checkBox.setVisibility(View.VISIBLE);
                checkBox.setChecked(checked);
            } else {
                checkBox.setVisibility(View.GONE);
            }

            if (data.getType() == Notes.TYPE_FOLDER) {
                String snippet = data.getSnippet();
                if (snippet == null) snippet = "";
                title.setText(snippet + " (" + data.getNotesCount() + ")");
                time.setVisibility(View.GONE);
                typeIcon.setVisibility(View.VISIBLE);
                typeIcon.setImageResource(R.drawable.ic_folder);
                cardView.setCardBackgroundColor(mContext.getColor(R.color.bg_white));
            } else {
                typeIcon.setVisibility(View.GONE);
                time.setVisibility(View.VISIBLE);
                String titleStr = data.getTitle();
                if (titleStr == null || titleStr.isEmpty()) {
                    titleStr = data.getSnippet();
                }
                title.setText(titleStr);
                time.setText(DateUtils.getRelativeTimeSpanString(data.getModifiedDate()));
                
                // Background Color
                int colorId = data.getBgColorId();
                int color = ResourceParser.getNoteBgColor(mContext, colorId);
                cardView.setCardBackgroundColor(color);
            }
        }
    }
}