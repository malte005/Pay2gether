package com.maltedammann.pay2gether.pay2gether.user;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialogFragment;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import com.firebase.ui.auth.AuthUI;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.maltedammann.pay2gether.pay2gether.R;
import com.maltedammann.pay2gether.pay2gether.control.DbUtils;
import com.maltedammann.pay2gether.pay2gether.events.EventsActivity;
import com.maltedammann.pay2gether.pay2gether.main.MainActivity;
import com.maltedammann.pay2gether.pay2gether.model.User;
import com.maltedammann.pay2gether.pay2gether.utils.AddUserDialogFragment;
import com.maltedammann.pay2gether.pay2gether.utils.AuthUtils;
import com.maltedammann.pay2gether.pay2gether.utils.UIHelper;
import com.maltedammann.pay2gether.pay2gether.utils.extendables.BaseActivity;
import com.maltedammann.pay2gether.pay2gether.utils.interfaces.ItemAddedHandler;

import java.util.ArrayList;

import static com.maltedammann.pay2gether.pay2gether.user.UserHolder.CONTEXT_DELETE_ENTRY;
import static com.maltedammann.pay2gether.pay2gether.user.UserHolder.CONTEXT_EDIT_ENTRY;

public class UserActivity extends BaseActivity implements NavigationView.OnNavigationItemSelectedListener, ItemAddedHandler {

    // Object Holder
    private ArrayList<User> users = new ArrayList<>();

    //DB
    private DbUtils db;

    //Firebase instance variables
    private FirebaseUser currentUser;
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;
    private ChildEventListener mChildEventListener;

    //Constants
    private static final String TAG = UserActivity.class.getSimpleName();
    public static final String INTENT_DISPLAY_NAME = "display_name";
    public static final String INTENT_USER_ID = "user_id";
    private final int ADD_FRIEND = 123;

    //Result add Friend
    private boolean mReturningWithResult = false;
    private int result = 0;
    private int request = 0;
    private Intent intentContact = null;

    //UI
    private NavigationView navigationView;
    private RecyclerViewAdapterUser adapter;
    private RecyclerView mRecyclerView;
    private FloatingActionButton fab;
    private Toolbar toolbar;
    private SwipeRefreshLayout swipeRefreshLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friends);
        toolbar = (Toolbar) findViewById(R.id.toolbar_userProfile);
        setSupportActionBar(toolbar);

        //DB Helper connection
        db = new DbUtils(this);

        //DrawMenu init
        setupDrawer();

        // View + adapter init
        setupRecyclerView();

        // Fab init
        setupFab();

        //Firebase init
        setupFirebase();

        //Refresh by pull down
        setupPullRefresh();
    }

    private void setupPullRefresh() {
        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refreshList();
                UIHelper.snack(findViewById(R.id.clFriends), "Refreshed");
            }
        });
    }

    private void refreshList() {
        // showing refresh animation before making http call
        swipeRefreshLayout.setRefreshing(true);

        adapter.notifyDataSetChanged();

        // stopping swipe refresh
        swipeRefreshLayout.setRefreshing(false);
    }

    private void setupFirebase() {
        /**
         * Firebase - Auth
         */
        mFirebaseAuth = FirebaseAuth.getInstance();
        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                currentUser = firebaseAuth.getCurrentUser();
                if (currentUser != null) {
                    // User is signed in
                    Log.d(TAG, "onAuthStateChanged:signed_in:" + currentUser.getDisplayName());
                    onSignInInitializer();
                } else {
                    // User is signed out
                    Log.d(TAG, "onAuthStateChanged:signed_out");
                    onSignOutCleanup();
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(false)
                                    .setProviders(
                                            AuthUI.GOOGLE_PROVIDER
                                            , AuthUI.EMAIL_PROVIDER
                                            //,AuthUI.FACEBOOK_PROVIDER
                                    )
                                    .build(),
                            MainActivity.RC_SIGN_IN);
                }
            }
        };
    }

    private void onSignOutCleanup() {
        detachDatabaseListener();
    }

    private void onSignInInitializer() {
        attachDatabaseReadListener();
    }

    private void attachDatabaseReadListener() {
        /**
         * Firebase - Read User
         */
        if (mChildEventListener == null) {
            mChildEventListener = new ChildEventListener() {
                @Override
                public void onChildAdded(com.google.firebase.database.DataSnapshot dataSnapshot, String s) {
                    getAllUser(dataSnapshot, true);
//                Log.d(TAG, "onChildAdd:" + dataSnapshot.getValue(User.class).getName());
                }

                @Override
                public void onChildChanged(com.google.firebase.database.DataSnapshot dataSnapshot, String s) {
                    getAllUser(dataSnapshot, false);
                    Log.d(TAG, "onChildChanged:" + dataSnapshot.getValue(User.class).getName());
                }

                @Override
                public void onChildRemoved(com.google.firebase.database.DataSnapshot dataSnapshot) {
                    Log.d(TAG, "onChildDeleted:" + dataSnapshot.getValue(User.class).getName());
                    //getAllUser(dataSnapshot, false);
                    User deleted = dataSnapshot.getValue(User.class);
                    adapter.remove();
                }

                @Override
                public void onChildMoved(com.google.firebase.database.DataSnapshot dataSnapshot, String s) {
                    Log.d(TAG, "onChildMoved:" + dataSnapshot.getValue(User.class).getName());
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    Log.d(TAG, "onChildChanged:" + " CANCELED");
                }
            };

            db.mFirebaseDbReference.child(db.USER_REF).addChildEventListener(mChildEventListener);
        }
    }

    private void detachDatabaseListener() {
        if (mChildEventListener != null) {
            db.mFirebaseDbReference.child(db.USER_REF).removeEventListener(mChildEventListener);
            mChildEventListener = null;
        }
    }

    private void setupDrawer() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();
        navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        navigationView.getMenu().getItem(2).setChecked(true);
    }

    private void setupFab() {
        fab = (FloatingActionButton) findViewById(R.id.fab_friends);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent addFriend = new Intent(UserActivity.this, AddUserActivity.class);
                startActivityForResult(addFriend, ADD_FRIEND);
            }
        });
    }

    private void setupRecyclerView() {
        mRecyclerView = (RecyclerView) findViewById(R.id.rv_friends);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        adapter = new RecyclerViewAdapterUser(UserActivity.this, users);
        mRecyclerView.setAdapter(adapter);

        registerForContextMenu(mRecyclerView);
    }

    private void getAllUser(DataSnapshot dataSnapshot, boolean addUser) {
        if (dataSnapshot != null && dataSnapshot.getValue() != null) {
            try {
                if (addUser) {
                    users.add(dataSnapshot.getValue(User.class));
                }
                adapter = new RecyclerViewAdapterUser(UserActivity.this, users);
                mRecyclerView.setAdapter(adapter);
                adapter.notifyDataSetChanged();
            } catch (Exception ex) {
                Log.e(TAG, ex.getMessage());
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        navigationView.getMenu().getItem(2).setChecked(true);
        mFirebaseAuth.addAuthStateListener(mAuthListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        navigationView.getMenu().getItem(2).setChecked(true);
        mFirebaseAuth.addAuthStateListener(mAuthListener);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mAuthListener != null) {
            mFirebaseAuth.removeAuthStateListener(mAuthListener);
        }
        detachDatabaseListener();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mAuthListener != null) {
            mFirebaseAuth.removeAuthStateListener(mAuthListener);
        }
        detachDatabaseListener();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mAuthListener != null) {
            mFirebaseAuth.removeAuthStateListener(mAuthListener);
        }
        detachDatabaseListener();
        //adapter.cleanup();
    }

    @Override
    public void onItemAdded(Object user) {
        User newUser = (User) user;
        db.addUser(newUser);
        UIHelper.snack((View) findViewById(R.id.clFriends), newUser.getName() + " added");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mReturningWithResult = true;
        request = requestCode;
        result = resultCode;
        intentContact = data;
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        if (mReturningWithResult) {
            if (request == ADD_FRIEND && intentContact != null) {
                if (result != Activity.RESULT_CANCELED || !intentContact.getStringExtra("display_name").isEmpty()) {
                    String name = intentContact.getStringExtra(INTENT_DISPLAY_NAME);
                    //String mail = intent.getStringExtra("mail_address");
                    AppCompatDialogFragment addFriendFragment = AddUserDialogFragment.newInstance(name, null);
                    addFriendFragment.show(getSupportFragmentManager(), "Add User");
                }
            }
        }
        // Reset the flags back to false for next time.
        mReturningWithResult = false;
        request = 0;
        result = 0;
        intentContact = null;
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();
        AlertDialog alert;

        if (id == R.id.nav_news) {
            finish();
        } else if (id == R.id.nav_events) {
            Intent openEvents = new Intent(this, EventsActivity.class);
            startActivity(openEvents);
            finish();
        } else if (id == R.id.nav_logout) {
            alert = (AlertDialog) AuthUtils.showLogoutDeleteDialog(this, getString(R.string.signOutText), getString(R.string.signOut));
            alert.show();
        } else if (id == R.id.nav_delete_acc) {
            alert = (AlertDialog) AuthUtils.showLogoutDeleteDialog(this, getString(R.string.signOutDeleteUser), getString(R.string.signOutDelete));
            alert.show();
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        //AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        //int index = info.position;
        User temp = adapter.getSelectedItem(item);
        switch (item.getItemId()) {
            case CONTEXT_EDIT_ENTRY:
                Intent go2Edit = new Intent(UserActivity.this, EditUserActivity.class);
                System.out.println("IDIDIDID:" + temp.getId());
                go2Edit.putExtra(INTENT_USER_ID, temp.getId());
                startActivity(go2Edit);
                break;
            case CONTEXT_DELETE_ENTRY:
                db.deleteUser(temp.getId());
                //UIHelper.snack(findViewById(R.id.clFriends), temp.getName() + " deleted");
                adapter.notifyDataSetChanged();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

}