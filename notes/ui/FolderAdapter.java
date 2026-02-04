package net.micode.notes.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import net.micode.notes.R;
import net.micode.notes.data.NotesRepository;

import java.util.ArrayList;
import java.util.List;

public class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.FolderViewHolder> {

    private Context context;
    private List<NotesRepository.NoteInfo> folders;
    private long selectedFolderId = -1;
    private OnFolderClickListener listener;

    public interface OnFolderClickListener {
        void onFolderClick(long folderId);
    }

    public FolderAdapter(Context context) {
        this.context = context;
        this.folders = new ArrayList<>();
    }

    public void setFolders(List<NotesRepository.NoteInfo> folders) {
        this.folders = folders != null ? folders : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setSelectedFolderId(long folderId) {
        this.selectedFolderId = folderId;
        notifyDataSetChanged();
    }

    public void setOnFolderClickListener(OnFolderClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public FolderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.folder_tab_item, parent, false);
        return new FolderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FolderViewHolder holder, int position) {
        NotesRepository.NoteInfo folder = folders.get(position);
        holder.bind(folder);
    }

    @Override
    public int getItemCount() {
        return folders.size();
    }

    class FolderViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;

        public FolderViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_folder_name);
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        listener.onFolderClick(folders.get(pos).getId());
                    }
                }
            });
        }

        public void bind(NotesRepository.NoteInfo folder) {
            String name = folder.snippet; // Folder name is stored in snippet
            if (name == null || name.isEmpty()) {
                name = "Folder";
            }
            tvName.setText(name);
            tvName.setSelected(folder.getId() == selectedFolderId);
        }
    }
}