package net.jejer.hipda.ui.adapter;

import android.content.Context;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import net.jejer.hipda.R;
import net.jejer.hipda.bean.ContentAbs;
import net.jejer.hipda.bean.ContentAttach;
import net.jejer.hipda.bean.ContentGoToFloor;
import net.jejer.hipda.bean.ContentImg;
import net.jejer.hipda.bean.ContentQuote;
import net.jejer.hipda.bean.ContentText;
import net.jejer.hipda.bean.DetailBean;
import net.jejer.hipda.bean.HiSettingsHelper;
import net.jejer.hipda.cache.ImageContainer;
import net.jejer.hipda.glide.GlideHelper;
import net.jejer.hipda.glide.GlideImageEvent;
import net.jejer.hipda.glide.GlideImageView;
import net.jejer.hipda.glide.ImageReadyInfo;
import net.jejer.hipda.job.GlideImageJob;
import net.jejer.hipda.job.JobMgr;
import net.jejer.hipda.ui.TextViewWithEmoticon;
import net.jejer.hipda.ui.ThreadDetailFragment;
import net.jejer.hipda.ui.ThreadImageLayout;
import net.jejer.hipda.utils.Utils;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by GreenSkinMonster on 2016-11-08.
 */

public class ThreadDetailAdapter extends BaseRvAdapter<DetailBean> {

    private Context mCtx;
    private LayoutInflater mInflater;
    private Button.OnClickListener mGoToFloorListener;
    private View.OnClickListener mAvatarListener;
    private ThreadDetailFragment mDetailFragment;
    private long delayAnimDeadline = 0;

    private Map<String, Map<Integer, ThreadImageLayout>> imageLayoutMap = new HashMap<>();

    public ThreadDetailAdapter(Context context, ThreadDetailFragment detailFragment, RecyclerItemClickListener listener,
                               Button.OnClickListener gotoFloorListener, View.OnClickListener avatarListener) {
        mCtx = context;
        mInflater = LayoutInflater.from(context);
        mListener = listener;
        mGoToFloorListener = gotoFloorListener;
        mAvatarListener = avatarListener;
        mDetailFragment = detailFragment;
        delayAnimDeadline = System.currentTimeMillis() + context.getResources().getInteger(R.integer.defaultAnimTime) + 50;
    }

    @Override
    public ViewHolderImpl onCreateViewHolderImpl(ViewGroup parent, int position) {
        return new ViewHolderImpl(mInflater.inflate(R.layout.item_thread_detail, parent, false));
    }

    @Override
    public void onBindViewHolderImpl(RecyclerView.ViewHolder viewHolder, int position) {
        ViewHolderImpl holder;
        if (viewHolder instanceof ViewHolderImpl)
            holder = (ViewHolderImpl) viewHolder;
        else return;

        viewHolder.itemView.setTag(position);
        viewHolder.itemView.setOnTouchListener(mListener);

        DetailBean detail = getItem(position);

        holder.author.setText(detail.getAuthor());
        holder.time.setText(Utils.shortyTime(detail.getTimePost()));
        holder.floor.setText(detail.getFloor() + "#");

        boolean trimBr = false;
        String postStaus = detail.getPostStatus();
        if (postStaus != null && postStaus.length() > 0) {
            holder.postStatus.setText(postStaus);
            holder.postStatus.setVisibility(View.VISIBLE);
            trimBr = true;
        } else {
            holder.postStatus.setVisibility(View.GONE);
        }

        if (HiSettingsHelper.getInstance().isLoadAvatar()) {
            holder.avatar.setVisibility(View.VISIBLE);
            loadAvatar(detail.getAvatarUrl(), holder.avatar);
        } else {
            holder.avatar.setVisibility(View.GONE);
        }
        holder.avatar.setTag(R.id.avatar_tag_uid, detail.getUid());
        holder.avatar.setTag(R.id.avatar_tag_username, detail.getAuthor());
        holder.avatar.setOnClickListener(mAvatarListener);

        holder.author.setTag(R.id.avatar_tag_uid, detail.getUid());
        holder.author.setTag(R.id.avatar_tag_username, detail.getAuthor());
        holder.author.setOnClickListener(mAvatarListener);

        LinearLayout contentView = holder.contentView;
        contentView.removeAllViews();
        contentView.bringToFront();

        for (int i = 0; i < detail.getContents().getSize(); i++) {
            ContentAbs content = detail.getContents().get(i);
            if (content instanceof ContentText) {
                TextViewWithEmoticon tv = (TextViewWithEmoticon) mInflater.inflate(R.layout.item_textview_withemoticon, null, false);
                tv.setFragment(mDetailFragment);
                tv.setTextSize(HiSettingsHelper.getInstance().getPostTextSize());
                tv.setPadding(8, 8, 8, 8);

                String cnt = content.getContent();
                if (trimBr)
                    cnt = Utils.removeLeadingBlank(cnt);
                if (!TextUtils.isEmpty(cnt)) {
                    tv.setText(cnt);
                    tv.setFocusable(false);
                    contentView.addView(tv);
                }
            } else if (content instanceof ContentImg) {
                final ContentImg contentImg = ((ContentImg) content);

                final String imageUrl = contentImg.getContent();
                int imageIndex = contentImg.getIndexInPage();

                final ThreadImageLayout threadImageLayout = new ThreadImageLayout(mCtx);
                final GlideImageView giv = threadImageLayout.getImageView();

                giv.setFragment(mDetailFragment);
                giv.setFocusable(false);
                giv.setClickable(true);

                Map<Integer, ThreadImageLayout> subImageMap;
                if (imageLayoutMap.containsKey(imageUrl)) {
                    subImageMap = imageLayoutMap.get(imageUrl);
                } else {
                    subImageMap = new HashMap<>();
                }
                subImageMap.put(imageIndex, threadImageLayout);
                imageLayoutMap.put(imageUrl, subImageMap);

                ImageReadyInfo imageReadyInfo = ImageContainer.getImageInfo(imageUrl);

                RelativeLayout.LayoutParams params;
                if (imageReadyInfo != null && imageReadyInfo.isReady()) {
                    params = new RelativeLayout.LayoutParams(imageReadyInfo.getDisplayWidth(), imageReadyInfo.getDisplayHeight());
                } else {
                    params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Utils.dpToPx(mCtx, 150));
                    giv.setImageDrawable(ContextCompat.getDrawable(mCtx, R.drawable.ic_action_image));
                }
                giv.setLayoutParams(params);
                contentView.addView(threadImageLayout);

                giv.setUrl(imageUrl);
                giv.setImageIndex(imageIndex);

                //delay images 50ms more than avatar
                long delay = delayAnimDeadline + 50 - System.currentTimeMillis();
                if (imageReadyInfo != null && imageReadyInfo.isReady()) {
                    loadImage(imageUrl, giv, delay);
                } else {
                    boolean imageLoadable = HiSettingsHelper.getInstance().isImageLoadable(contentImg.getFileSize());
                    if (contentImg.getFileSize() > 0) {
                        threadImageLayout.getImageInfoTextView().setVisibility(View.VISIBLE);
                        threadImageLayout.getImageInfoTextView().setText(Utils.toSizeText(contentImg.getFileSize()));
                    }
                    if (!imageLoadable) {
                        giv.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                JobMgr.addJob(new GlideImageJob(mDetailFragment, imageUrl, JobMgr.PRIORITY_LOW, mDetailFragment.mSessionId, true));
                                giv.setOnClickListener(null);
                            }
                        });
                    }
                    JobMgr.addJob(new GlideImageJob(
                            mDetailFragment,
                            imageUrl,
                            JobMgr.PRIORITY_LOW,
                            mDetailFragment.mSessionId,
                            imageLoadable,
                            delay));
                }

            } else if (content instanceof ContentAttach) {
                TextViewWithEmoticon tv = (TextViewWithEmoticon) mInflater.inflate(R.layout.item_textview_withemoticon, null, false);
                tv.setFragment(mDetailFragment);
                tv.setTextSize(HiSettingsHelper.getInstance().getPostTextSize());
                tv.setText(content.getContent());
                tv.setFocusable(false);
                contentView.addView(tv);
            } else if (content instanceof ContentQuote && !((ContentQuote) content).isReplyQuote()) {

                LinearLayout quoteLayout = (LinearLayout) mInflater.inflate(R.layout.item_quote_text_simple, null, false);
                TextViewWithEmoticon tv = (TextViewWithEmoticon) quoteLayout.findViewById(R.id.quote_content);
                tv.setFragment(mDetailFragment);

                tv.setTextSize(HiSettingsHelper.getInstance().getPostTextSize() - 1);
                tv.setAutoLinkMask(Linkify.WEB_URLS);
                tv.setText(Utils.removeLeadingBlank(content.getContent()));
                tv.setFocusable(false);    // make convertView long clickable.

                contentView.addView(quoteLayout);
                trimBr = true;
            } else if (content instanceof ContentGoToFloor || content instanceof ContentQuote) {

                String author = "";
                String time = "";
                String note = "";
                String text = "";

                int floor = -1;
                if (content instanceof ContentGoToFloor) {
                    //floor is not accurate if some user deleted post
                    //use floor to get page, then get cache by postid
                    ContentGoToFloor goToFloor = (ContentGoToFloor) content;
                    author = goToFloor.getAuthor();
                    floor = goToFloor.getFloor();
                    DetailBean detailBean = mDetailFragment.getCachedPost(goToFloor.getPostId());
                    if (detailBean != null) {
                        text = detailBean.getContents().getContent();
                        floor = detailBean.getFloor();
                    }
                    note = floor + "#";
                } else {
                    ContentQuote contentQuote = (ContentQuote) content;
                    DetailBean detailBean = null;
                    if (!TextUtils.isEmpty(contentQuote.getPostId()) && TextUtils.isDigitsOnly(contentQuote.getPostId())) {
                        detailBean = mDetailFragment.getCachedPost(contentQuote.getPostId());
                    }
                    if (detailBean != null) {
                        author = contentQuote.getAuthor();
                        text = detailBean.getContents().getContent();
                        floor = detailBean.getFloor();
                        note = floor + "#";
                    } else {
                        author = ((ContentQuote) content).getAuthor();
                        if (!TextUtils.isEmpty(((ContentQuote) content).getTo()))
                            note = "to: " + ((ContentQuote) content).getTo();
                        time = ((ContentQuote) content).getTime();
                        text = ((ContentQuote) content).getText();
                    }
                }

                text = Utils.removeLeadingBlank(text);

                LinearLayout quoteLayout = (LinearLayout) mInflater.inflate(R.layout.item_quote_text, null, false);

                TextView tvAuthor = (TextView) quoteLayout.findViewById(R.id.quote_author);
                TextView tvNote = (TextView) quoteLayout.findViewById(R.id.quote_note);
                TextViewWithEmoticon tvContent = (TextViewWithEmoticon) quoteLayout.findViewById(R.id.quote_content);
                TextView tvTime = (TextView) quoteLayout.findViewById(R.id.quote_post_time);

                tvContent.setFragment(mDetailFragment);
                tvContent.setTrim(true);

                tvAuthor.setText(Utils.nullToText(author));
                tvNote.setText(Utils.nullToText(note));
                tvContent.setText(Utils.nullToText(text));
                tvTime.setText(Utils.nullToText(time));

                tvAuthor.setTextSize(HiSettingsHelper.getInstance().getPostTextSize() - 2);
                tvNote.setTextSize(HiSettingsHelper.getInstance().getPostTextSize() - 2);
                tvContent.setTextSize(HiSettingsHelper.getInstance().getPostTextSize() - 1);
                tvTime.setTextSize(HiSettingsHelper.getInstance().getPostTextSize() - 4);

                if (floor > 0) {
                    tvNote.setTag(floor);
                    tvNote.setOnClickListener(mGoToFloorListener);
                    tvNote.setFocusable(false);
                    tvNote.setClickable(true);
                }

                contentView.addView(quoteLayout);
                trimBr = true;
            }
        }
    }

    private void loadImage(final String imageUrl, final GlideImageView giv, long delay) {
        if (delay > 0) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    mDetailFragment.loadImage(imageUrl, giv);
                }
            }, delay);
        } else {
            mDetailFragment.loadImage(imageUrl, giv);
        }
    }

    private void loadAvatar(final String avatarUrl, final ImageView imageView) {
        long delay = delayAnimDeadline - System.currentTimeMillis();
        if (delay > 0) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    GlideHelper.loadAvatar(mDetailFragment, imageView, avatarUrl);
                }
            }, delay);
        } else {
            GlideHelper.loadAvatar(mDetailFragment, imageView, avatarUrl);
        }
    }

    public int getPositionByFloor(int floor) {
        List<DetailBean> datas = getDatas();
        for (int i = 0; i < datas.size(); i++) {
            DetailBean bean = datas.get(i);
            if (bean.getFloor() == floor) {
                return i + getHeaderCount();
            }
        }
        return -1;
    }

    public int getPositionByPostId(String postId) {
        List<DetailBean> datas = getDatas();
        for (int i = 0; i < datas.size(); i++) {
            DetailBean bean = datas.get(i);
            if (bean.getPostId().equals(postId)) {
                return i + getHeaderCount();
            }
        }
        return -1;
    }

    private static class ViewHolderImpl extends RecyclerView.ViewHolder {
        ImageView avatar;
        TextView author;
        TextView floor;
        TextView postStatus;
        TextView time;
        LinearLayout contentView;

        ViewHolderImpl(View itemView) {
            super(itemView);
            avatar = (ImageView) itemView.findViewById(R.id.iv_avatar);
            author = (TextView) itemView.findViewById(R.id.tv_username);
            time = (TextView) itemView.findViewById(R.id.time);
            floor = (TextView) itemView.findViewById(R.id.floor);
            postStatus = (TextView) itemView.findViewById(R.id.post_status);
            contentView = (LinearLayout) itemView.findViewById(R.id.content_layout);
        }
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(GlideImageEvent event) {
        String imageUrl = event.getImageUrl();
        if (!TextUtils.isEmpty(imageUrl)
                && imageLayoutMap.containsKey(imageUrl)) {
            Map<Integer, ThreadImageLayout> subImageMap = imageLayoutMap.get(imageUrl);
            for (ThreadImageLayout layout : subImageMap.values()) {
                ProgressBar bar = layout.getProgressBar();
                if (ViewCompat.isAttachedToWindow(layout)) {
                    if (event.isInProgress()) {
                        if (bar.getVisibility() != View.VISIBLE)
                            bar.setVisibility(View.VISIBLE);
                        bar.setProgress(event.getProgress());
                    } else {
                        if (bar.getVisibility() == View.VISIBLE)
                            bar.setVisibility(View.GONE);
                        TextView imageInfo = layout.getImageInfoTextView();
                        GlideImageView giv = layout.getImageView();
                        mDetailFragment.loadImage(imageUrl, giv);
                        if (imageInfo.getVisibility() == View.VISIBLE) {
                            imageInfo.setVisibility(View.GONE);
                        }
                    }
                }
            }
        }
    }

}