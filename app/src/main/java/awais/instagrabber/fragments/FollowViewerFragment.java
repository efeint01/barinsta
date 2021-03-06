package awais.instagrabber.fragments;

import android.content.Context;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;

import awais.instagrabber.R;
import awais.instagrabber.adapters.FollowAdapter;
import awais.instagrabber.customviews.helpers.RecyclerLazyLoader;
import awais.instagrabber.databinding.FragmentFollowersViewerBinding;
import awais.instagrabber.models.FollowModel;
import awais.instagrabber.repositories.responses.FriendshipRepoListFetchResponse;
import awais.instagrabber.utils.TextUtils;
import awais.instagrabber.webservices.FriendshipService;
import awais.instagrabber.webservices.ServiceCallback;
import thoughtbot.expandableadapter.ExpandableGroup;

public final class FollowViewerFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {
    private static final String TAG = "FollowViewerFragment";

    private final ArrayList<FollowModel> followModels = new ArrayList<>();
    private final ArrayList<FollowModel> followingModels = new ArrayList<>();
    private final ArrayList<FollowModel> followersModels = new ArrayList<>();
    private final ArrayList<FollowModel> allFollowing = new ArrayList<>();

    private boolean moreAvailable = true, isFollowersList, isCompare = false, loading = false, shouldRefresh = true;
    private String profileId, username, namePost, type, endCursor;
    private Resources resources;
    private LinearLayoutManager layoutManager;
    private RecyclerLazyLoader lazyLoader;
    private FollowModel model;
    private FollowAdapter adapter;
    private View.OnClickListener clickListener;
    private FragmentFollowersViewerBinding binding;
    private AsyncTask<Void, Void, FollowModel[]> currentlyExecuting;
    private SwipeRefreshLayout root;
    private FriendshipService friendshipService;
    private AppCompatActivity fragmentActivity;

    final ServiceCallback<FriendshipRepoListFetchResponse> followingFetchCb = new ServiceCallback<FriendshipRepoListFetchResponse>() {
        @Override
        public void onSuccess(final FriendshipRepoListFetchResponse result) {
            if (result != null) {
                followingModels.addAll(result.getItems());
                if (!isFollowersList) followModels.addAll(result.getItems());
                if (result.isMoreAvailable()) {
                    endCursor = result.getNextMaxId();
                    friendshipService.getList(false, profileId, endCursor, this);
                } else if (followersModels.size() == 0) {
                    if (!isFollowersList) moreAvailable = false;
                    friendshipService.getList(true, profileId, null, followingFetchCb);
                } else {
                    if (!isFollowersList) moreAvailable = false;
                    showCompare();
                }
            } else binding.swipeRefreshLayout.setRefreshing(false);
        }

        @Override
        public void onFailure(final Throwable t) {
            binding.swipeRefreshLayout.setRefreshing(false);
            Toast.makeText(getContext(), t.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error fetching list (double, following)", t);
        }
    };
    final ServiceCallback<FriendshipRepoListFetchResponse> followersFetchCb = new ServiceCallback<FriendshipRepoListFetchResponse>() {
        @Override
        public void onSuccess(final FriendshipRepoListFetchResponse result) {
            if (result != null) {
                followersModels.addAll(result.getItems());
                if (isFollowersList) followModels.addAll(result.getItems());
                if (result.isMoreAvailable()) {
                    endCursor = result.getNextMaxId();
                    friendshipService.getList(true, profileId, endCursor, this);
                } else if (followingModels.size() == 0) {
                    if (isFollowersList) moreAvailable = false;
                    friendshipService.getList(false, profileId, null, followingFetchCb);
                } else {
                    if (isFollowersList) moreAvailable = false;
                    showCompare();
                }
            }
        }

        @Override
        public void onFailure(final Throwable t) {
            binding.swipeRefreshLayout.setRefreshing(false);
            Toast.makeText(getContext(), t.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error fetching list (double, follower)", t);
        }
    };

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        friendshipService = FriendshipService.getInstance();
        fragmentActivity = (AppCompatActivity) getActivity();
        setHasOptionsMenu(true);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable final Bundle savedInstanceState) {
        if (root != null) {
            shouldRefresh = false;
            return root;
        }
        binding = FragmentFollowersViewerBinding.inflate(getLayoutInflater());
        root = binding.getRoot();
        return root;
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable final Bundle savedInstanceState) {
        if (!shouldRefresh) return;
        init();
        shouldRefresh = false;
    }

    private void init() {
        if (getArguments() == null) return;
        final FollowViewerFragmentArgs fragmentArgs = FollowViewerFragmentArgs.fromBundle(getArguments());
        profileId = fragmentArgs.getProfileId();
        isFollowersList = fragmentArgs.getIsFollowersList();
        username = fragmentArgs.getUsername();
        namePost = username;
        if (TextUtils.isEmpty(username)) {
            // this usually should not occur
            username = "You";
            namePost = "You're";
        }
        setTitle(username);
        resources = getResources();
        clickListener = v -> {
            final Object tag = v.getTag();
            if (tag instanceof FollowModel) {
                model = (FollowModel) tag;
                final FollowViewerFragmentDirections.ActionFollowViewerFragmentToProfileFragment action = FollowViewerFragmentDirections
                        .actionFollowViewerFragmentToProfileFragment();
                action.setUsername("@" + model.getUsername());
                NavHostFragment.findNavController(this).navigate(action);
            }
        };
        binding.swipeRefreshLayout.setOnRefreshListener(this);
        onRefresh();
    }

    private void setTitle(final String title) {
        final ActionBar actionBar = fragmentActivity.getSupportActionBar();
        if (actionBar == null) return;
        actionBar.setTitle(title);
    }

    private void setSubtitle(final String subtitle) {
        final ActionBar actionBar = fragmentActivity.getSupportActionBar();
        if (actionBar == null) return;
        actionBar.setSubtitle(subtitle);
    }

    private void setSubtitle(@SuppressWarnings("SameParameterValue") final int subtitleRes) {
        final ActionBar actionBar = fragmentActivity.getSupportActionBar();
        if (actionBar == null) return;
        actionBar.setSubtitle(subtitleRes);
    }

    @Override
    public void onRefresh() {
        if (isCompare) listCompare();
        else listFollows();
        endCursor = null;
        lazyLoader.resetState();
    }

    private void listFollows() {
        type = resources.getString(isFollowersList ? R.string.followers_type_followers : R.string.followers_type_following);
        setSubtitle(type);
        final ServiceCallback<FriendshipRepoListFetchResponse> cb = new ServiceCallback<FriendshipRepoListFetchResponse>() {
            @Override
            public void onSuccess(final FriendshipRepoListFetchResponse result) {
                if (result == null) {
                    binding.swipeRefreshLayout.setRefreshing(false);
                    return;
                }
                else {
                    int oldSize = followModels.size() == 0 ? 0 : followModels.size() - 1;
                    followModels.addAll(result.getItems());
                    if (result.isMoreAvailable()) {
                        moreAvailable = true;
                        endCursor = result.getNextMaxId();
                    }
                    else moreAvailable = false;
                    binding.swipeRefreshLayout.setRefreshing(false);
                    if (isFollowersList) followersModels.addAll(result.getItems());
                    else followingModels.addAll(result.getItems());
                    refreshAdapter(followModels, null, null, null);
                    layoutManager.scrollToPosition(oldSize);
                }
            }

            @Override
            public void onFailure(final Throwable t) {
                binding.swipeRefreshLayout.setRefreshing(false);
                Toast.makeText(getContext(), t.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Error fetching list (single)", t);
            }
        };
        layoutManager = new LinearLayoutManager(getContext());
        lazyLoader = new RecyclerLazyLoader(layoutManager, (page, totalItemsCount) -> {
            if (!TextUtils.isEmpty(endCursor)) {
                binding.swipeRefreshLayout.setRefreshing(true);
                layoutManager.setStackFromEnd(true);
                friendshipService.getList(isFollowersList, profileId, endCursor, cb);
            }
            endCursor = null;
        });
        binding.rvFollow.addOnScrollListener(lazyLoader);
        binding.rvFollow.setLayoutManager(layoutManager);
        if (moreAvailable) {
            binding.swipeRefreshLayout.setRefreshing(true);
            friendshipService.getList(isFollowersList, profileId, endCursor, cb);
        }
        else {
            refreshAdapter(followModels, null, null, null);
            layoutManager.scrollToPosition(0);
        }
    }

    private void listCompare() {
        layoutManager.setStackFromEnd(false);
        binding.rvFollow.clearOnScrollListeners();
        loading = true;
        setSubtitle(R.string.followers_compare);
        allFollowing.clear();
        if (moreAvailable) {
            binding.swipeRefreshLayout.setRefreshing(true);
            Toast.makeText(getContext(), R.string.follower_start_compare, Toast.LENGTH_LONG).show();
            friendshipService.getList(isFollowersList,
                    profileId,
                    endCursor,
                    isFollowersList ? followersFetchCb : followingFetchCb);
        }
        else if (followersModels.size() == 0 || followingModels.size() == 0) {
            binding.swipeRefreshLayout.setRefreshing(true);
            Toast.makeText(getContext(), R.string.follower_start_compare, Toast.LENGTH_LONG).show();
            friendshipService.getList(!isFollowersList,
                    profileId,
                    null,
                    isFollowersList ? followingFetchCb : followersFetchCb);
        }
        else showCompare();
    }

    private void showCompare() {
        allFollowing.addAll(followersModels);
        allFollowing.retainAll(followingModels);

        for (final FollowModel followModel : allFollowing) {
            followersModels.remove(followModel);
            followingModels.remove(followModel);
        }

        allFollowing.trimToSize();
        followersModels.trimToSize();
        followingModels.trimToSize();

        binding.swipeRefreshLayout.setRefreshing(false);

        refreshAdapter(null, followingModels, followersModels, allFollowing);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.follow, menu);
        final MenuItem menuSearch = menu.findItem(R.id.action_search);
        final SearchView searchView = (SearchView) menuSearch.getActionView();
        searchView.setQueryHint(getResources().getString(R.string.action_search));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            // private final Filter filter = new Filter() {
            //     private final ArrayList<FollowModel> searchFollowModels = new ArrayList<>(followModels.size() / 2);
            //     private final ArrayList<FollowModel> searchFollowingModels = new ArrayList<>(followingModels.size() / 2);
            //     private final ArrayList<FollowModel> searchFollowersModels = new ArrayList<>(followersModels.size() / 2);
            //     private final ArrayList<FollowModel> searchAllFollowing = new ArrayList<>(allFollowing.size() / 2);
            //
            //     @Nullable
            //     @Override
            //     protected FilterResults performFiltering(@NonNull final CharSequence constraint) {
            //         searchFollowModels.clear();
            //         searchFollowingModels.clear();
            //         searchFollowersModels.clear();
            //         searchAllFollowing.clear();
            //
            //         final int followModelsSize = followModels.size();
            //         final int followingModelsSize = followingModels.size();
            //         final int followersModelsSize = followersModels.size();
            //         final int allFollowingSize = allFollowing.size();
            //
            //         int maxSize = followModelsSize;
            //         if (maxSize < followingModelsSize) maxSize = followingModelsSize;
            //         if (maxSize < followersModelsSize) maxSize = followersModelsSize;
            //         if (maxSize < allFollowingSize) maxSize = allFollowingSize;
            //
            //         final String query = constraint.toString().toLowerCase();
            //         FollowModel followModel;
            //         while (maxSize != -1) {
            //             if (maxSize < followModelsSize) {
            //                 followModel = followModels.get(maxSize);
            //                 if (Utils.hasKey(query, followModel.getUsername(), followModel.getFullName()))
            //                     searchFollowModels.add(followModel);
            //             }
            //
            //             if (maxSize < followingModelsSize) {
            //                 followModel = followingModels.get(maxSize);
            //                 if (Utils.hasKey(query, followModel.getUsername(), followModel.getFullName()))
            //                     searchFollowingModels.add(followModel);
            //             }
            //
            //             if (maxSize < followersModelsSize) {
            //                 followModel = followersModels.get(maxSize);
            //                 if (Utils.hasKey(query, followModel.getUsername(), followModel.getFullName()))
            //                     searchFollowersModels.add(followModel);
            //             }
            //
            //             if (maxSize < allFollowingSize) {
            //                 followModel = allFollowing.get(maxSize);
            //                 if (Utils.hasKey(query, followModel.getUsername(), followModel.getFullName()))
            //                     searchAllFollowing.add(followModel);
            //             }
            //
            //             --maxSize;
            //         }
            //
            //         return null;
            //     }
            //
            //     @Override
            //     protected void publishResults(final CharSequence query, final FilterResults results) {
            //         refreshAdapter(searchFollowModels, searchFollowingModels, searchFollowersModels, searchAllFollowing);
            //     }
            // };

            @Override
            public boolean onQueryTextSubmit(final String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(final String query) {
                // if (Utils.isEmpty(query)) refreshAdapter(followModels, followingModels, followersModels, allFollowing);
                // else filter.filter(query.toLowerCase());
                if (adapter != null) adapter.getFilter().filter(query);
                return true;
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        if (item.getItemId() != R.id.action_compare) return super.onOptionsItemSelected(item);
        binding.rvFollow.setAdapter(null);
        final Context context = getContext();
        if (loading) Toast.makeText(context, R.string.follower_wait_to_load, Toast.LENGTH_LONG).show();
        else if (isCompare) {
            isCompare = !isCompare;
            listFollows();
        }
        else {
            isCompare = !isCompare;
            listCompare();
        }
        return true;
    }

    private void refreshAdapter(final ArrayList<FollowModel> followModels,
                                final ArrayList<FollowModel> followingModels,
                                final ArrayList<FollowModel> followersModels,
                                final ArrayList<FollowModel> allFollowing) {
        loading = false;
        final ArrayList<ExpandableGroup> groups = new ArrayList<>(1);

        if (isCompare) {
            if (followingModels != null && followingModels.size() > 0)
                groups.add(new ExpandableGroup(resources.getString(R.string.followers_not_following, username), followingModels));
            if (followersModels != null && followersModels.size() > 0)
                groups.add(new ExpandableGroup(resources.getString(R.string.followers_not_follower, namePost), followersModels));
            if (allFollowing != null && allFollowing.size() > 0)
                groups.add(new ExpandableGroup(resources.getString(R.string.followers_both_following), allFollowing));
        } else {
            groups.add(new ExpandableGroup(type, followModels));
        }
        adapter = new FollowAdapter(clickListener, groups);
        adapter.toggleGroup(0);
        binding.rvFollow.setAdapter(adapter);
    }
}