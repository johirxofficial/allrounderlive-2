package com.sportslive.hq.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.sportslive.hq.app.R;
import com.sportslive.hq.app.models.Event;
import java.util.List;

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {

    public interface OnEventClickListener {
        void onEventClick(Event event);
    }

    private final List<Event> eventList;
    private final OnEventClickListener listener;

    public EventAdapter(List<Event> eventList, OnEventClickListener listener) {
        this.eventList = eventList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_event_card, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        Event event = eventList.get(position);

        holder.tvTitle.setText(event.title);
        holder.tvDescription.setText(event.description);

        holder.tvTeamOneName.setText(event.teamOne != null ? event.teamOne.name : "");
        holder.tvTeamTwoName.setText(event.teamTwo != null ? event.teamTwo.name : "");

        Glide.with(holder.itemView.getContext())
                .load(event.teamOne != null ? event.teamOne.logo : null)
                .placeholder(R.drawable.circle_placeholder)
                .error(R.drawable.circle_placeholder)
                .into(holder.imgTeamOne);

        Glide.with(holder.itemView.getContext())
                .load(event.teamTwo != null ? event.teamTwo.logo : null)
                .placeholder(R.drawable.circle_placeholder)
                .error(R.drawable.circle_placeholder)
                .into(holder.imgTeamTwo);

        if (event.isLive()) {
            holder.tvBadge.setText(R.string.badge_live);
            holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_live);
        } else {
            holder.tvBadge.setText(R.string.badge_coming_soon);
            holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_soon);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onEventClick(event);
        });
    }

    @Override
    public int getItemCount() {
        return eventList != null ? eventList.size() : 0;
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {
        ImageView imgTeamOne, imgTeamTwo;
        TextView tvTeamOneName, tvTeamTwoName, tvTitle, tvDescription, tvBadge;

        EventViewHolder(@NonNull View itemView) {
            super(itemView);
            imgTeamOne = itemView.findViewById(R.id.imgTeamOne);
            imgTeamTwo = itemView.findViewById(R.id.imgTeamTwo);
            tvTeamOneName = itemView.findViewById(R.id.tvTeamOneName);
            tvTeamTwoName = itemView.findViewById(R.id.tvTeamTwoName);
            tvTitle = itemView.findViewById(R.id.tvEventTitle);
            tvDescription = itemView.findViewById(R.id.tvEventDescription);
            tvBadge = itemView.findViewById(R.id.tvStatusBadge);
        }
    }
}
