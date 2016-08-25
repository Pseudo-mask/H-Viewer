package ml.puredark.hviewer.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.OrientationHelper;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.github.clans.fab.FloatingActionMenu;
import com.wuxiaolong.pullloadmorerecyclerview.PullLoadMoreRecyclerView;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import ml.puredark.hviewer.HViewerApplication;
import ml.puredark.hviewer.R;
import ml.puredark.hviewer.adapters.PictureAdapter;
import ml.puredark.hviewer.adapters.TagAdapter;
import ml.puredark.hviewer.adapters.ViewPagerAdapter;
import ml.puredark.hviewer.beans.DownloadTask;
import ml.puredark.hviewer.beans.Tag;
import ml.puredark.hviewer.customs.AutoFitGridLayoutManager;
import ml.puredark.hviewer.customs.AutoFitStaggeredGridLayoutManager;
import ml.puredark.hviewer.customs.ExTabLayout;
import ml.puredark.hviewer.customs.ExViewPager;
import ml.puredark.hviewer.customs.ScalingImageView;
import ml.puredark.hviewer.dataproviders.ListDataProvider;
import ml.puredark.hviewer.helpers.FastBlur;
import ml.puredark.hviewer.helpers.MDStatusBarCompat;
import ml.puredark.hviewer.utils.DensityUtil;

public class DownloadTaskActivity extends AnimationActivity {

    @BindView(R.id.coordinator_layout)
    CoordinatorLayout coordinatorLayout;
    @BindView(R.id.backdrop)
    ScalingImageView backdrop;
    @BindView(R.id.toolbar)
    Toolbar toolbar;
    @BindView(R.id.tab_layout)
    ExTabLayout tabLayout;
    @BindView(R.id.view_pager)
    ExViewPager viewPager;
    @BindView(R.id.btn_return)
    ImageView btnReturn;
    @BindView(R.id.app_bar)
    AppBarLayout appBar;
    @BindView(R.id.fab_menu)
    FloatingActionMenu fabMenu;

    private DownloadTask task;

    private PullLoadMoreRecyclerView rvIndex;

    private PictureAdapter pictureAdapter;

    private PictureViewerActivity.PicturePagerAdapter picturePagerAdapter;

    private CollectionViewHolder holder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_collection);
        ButterKnife.bind(this);
        MDStatusBarCompat.setCollapsingToolbar(this, coordinatorLayout, appBar, backdrop, toolbar);

        setContainer(coordinatorLayout);

        /* 为返回按钮加载图标 */
        setReturnButton(btnReturn);
        setAppBar(appBar);
        setFabMenu(fabMenu);
        fabMenu.setVisibility(View.GONE);

        //获取传递过来的Collection实例
        if (HViewerApplication.temp instanceof DownloadTask)
            task = (DownloadTask) HViewerApplication.temp;

        //获取失败则结束此界面
        if (task == null || task.collection == null) {
            finish();
            return;
        }

        toolbar.setTitle(task.collection.title);
        setSupportActionBar(toolbar);

        initCover(task.collection.cover);
        initTabAndViewPager();
        refreshDescription();
    }

    private void initCover(String cover) {
        if (cover != null && !DownloadTaskActivity.this.isDestroyed())
            Glide.with(DownloadTaskActivity.this).load(cover).asBitmap().into(new SimpleTarget<Bitmap>() {
                @Override
                public void onResourceReady(final Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            /* 给背景封面加上高斯模糊 */
                            final Bitmap overlay = FastBlur.doBlur(resource.copy(Bitmap.Config.ARGB_8888, true), 2, true);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    backdrop.setImageBitmap(overlay);
                                    /* 让背景的封面大图来回缓慢移动 */
                                    float targetY = (overlay.getHeight() > overlay.getWidth()) ? -0.4f : 0f;
                                    Animation translateAnimation = new TranslateAnimation(TranslateAnimation.RELATIVE_TO_SELF, 0f,
                                            TranslateAnimation.RELATIVE_TO_SELF, 0f,
                                            TranslateAnimation.RELATIVE_TO_SELF, 0f,
                                            TranslateAnimation.RELATIVE_TO_SELF, targetY);
                                    translateAnimation.setDuration(50000);
                                    translateAnimation.setRepeatCount(-1);
                                    translateAnimation.setRepeatMode(Animation.REVERSE);
                                    translateAnimation.setInterpolator(new LinearInterpolator());
                                    backdrop.startAnimation(translateAnimation);
                                }
                            });
                        }
                    }).start();
                }
            });
    }

    private void initTabAndViewPager() {
        //初始化Tab和ViewPager
        List<View> views = new ArrayList<>();
        View viewIndex = getLayoutInflater().inflate(R.layout.view_collection_index, null);
        View viewDescription = getLayoutInflater().inflate(R.layout.view_collection_desciption, null);
        holder = new CollectionViewHolder(viewDescription);

        views.add(viewIndex);
        views.add(viewDescription);
        List<String> titles = new ArrayList<>();
        titles.add("目录");
        titles.add("相关");

        ViewPagerAdapter viewPagerAdapter = new ViewPagerAdapter(views, titles);
        viewPager.setAdapter(viewPagerAdapter);
        tabLayout.setupWithViewPager(viewPager);

        //初始化相册目录
        rvIndex = (PullLoadMoreRecyclerView) viewIndex.findViewById(R.id.rv_index);
        pictureAdapter = new PictureAdapter(new ListDataProvider(task.collection.pictures));
        pictureAdapter.setCookie(task.collection.site.cookie);
        rvIndex.setAdapter(pictureAdapter);

        rvIndex.getRecyclerView().setClipToPadding(false);
        rvIndex.getRecyclerView().setPadding(
                DensityUtil.dp2px(this, 8),
                DensityUtil.dp2px(this, 16),
                DensityUtil.dp2px(this, 8),
                DensityUtil.dp2px(this, 16));

        pictureAdapter.setOnItemClickListener(new PictureAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(View v, int position) {
                picturePagerAdapter = new PictureViewerActivity.PicturePagerAdapter(task.collection.site, task.collection.pictures);
                HViewerApplication.temp = picturePagerAdapter;
                Intent intent = new Intent(DownloadTaskActivity.this, PictureViewerActivity.class);
                intent.putExtra("position", position);
                startActivity(intent);
            }
        });

        //根据item宽度自动设置spanCount
        GridLayoutManager layoutManager = new AutoFitGridLayoutManager(this, DensityUtil.dp2px(this, 100));
        rvIndex.getRecyclerView().setLayoutManager(layoutManager);
        rvIndex.setPullRefreshEnable(false);
        rvIndex.setPushRefreshEnable(false);

    }

    private void refreshDescription(){
        toolbar.setTitle(task.collection.title);
        holder.tvTitle.setText(task.collection.title);
        holder.tvUploader.setText(task.collection.uploader);
        holder.tvCategory.setText(task.collection.category);
        TagAdapter adapter = (TagAdapter) holder.rvTags.getAdapter();
        if (task.collection.tags != null) {
            adapter.getDataProvider().clear();
            adapter.getDataProvider().addAll(task.collection.tags);
        }
        adapter.notifyDataSetChanged();
        holder.rbRating.setRating(task.collection.rating);
        holder.tvSubmittime.setText(task.collection.datetime);
    }

    @OnClick(R.id.btn_return)
    void back() {
        onBackPressed();
    }

    @Override
    public void onResume() {
        super.onResume();
        picturePagerAdapter = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    public class CollectionViewHolder {
        @BindView(R.id.tv_title)
        TextView tvTitle;
        @BindView(R.id.tv_uploader)
        TextView tvUploader;
        @BindView(R.id.tv_category)
        TextView tvCategory;
        @BindView(R.id.rv_tags)
        RecyclerView rvTags;
        @BindView(R.id.rb_rating)
        RatingBar rbRating;
        @BindView(R.id.tv_submittime)
        TextView tvSubmittime;

        public CollectionViewHolder(View view) {
            ButterKnife.bind(this, view);
            rvTags.setAdapter(
                    new TagAdapter(
                            new ListDataProvider<>(
                                    new ArrayList<Tag>()
                            )
                    )
            );
            StaggeredGridLayoutManager layoutManager =
                    new AutoFitStaggeredGridLayoutManager(getApplicationContext(), OrientationHelper.HORIZONTAL);
            rvTags.setLayoutManager(layoutManager);
        }
    }
}
