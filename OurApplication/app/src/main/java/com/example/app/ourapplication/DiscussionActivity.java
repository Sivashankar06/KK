package com.example.app.ourapplication;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;
import android.view.LayoutInflater;
import com.example.app.ourapplication.database.DBHelper;
import com.example.app.ourapplication.rest.model.request.CommentFeedReqModel;
import com.example.app.ourapplication.rest.model.request.SubscribeReqModel;
import com.example.app.ourapplication.rest.model.response.Person;
import com.example.app.ourapplication.rest.model.response.SuccessRespModel;
import com.example.app.ourapplication.util.Helper;
import com.example.app.ourapplication.util.UI;
import com.example.app.ourapplication.wss.WebSocketClient;
import com.example.app.ourapplication.wss.WebSocketListener;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static com.example.app.ourapplication.rest.ApiUrls.WS_URL;

/**
 * Created by ROYSH on 8/3/2016.
 */
public class DiscussionActivity extends AppCompatActivity implements WebSocketListener {

    private List<Person> mComments = new ArrayList<>();
    private FeedRVAdapter mCommentListAdapter;
    private WebSocketClient mWebSocketClient;
    private final String TAG = DiscussionActivity.class.getSimpleName();
    private String keyid;
   // private String token;
    private DBHelper mDBHelper = new DBHelper(this);
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private static View view;
    private  String token;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.discussion);
        LayoutInflater inflater=getLayoutInflater();
        view=(View) inflater.inflate(R.layout.discussion,null);
         token = ((OurApplication) getApplicationContext()).getUserToken();
        ImageView  mSendButton = (ImageView) findViewById(R.id.send_button);
        final EditText  mMessageBox = (EditText) findViewById(R.id.msg_box);
        mWebSocketClient = ((OurApplication)getApplicationContext()).getClient();
        mWebSocketClient.addWebSocketListener(this);
        token = ((OurApplication)getApplicationContext()).getUserToken();
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.rv);
        LinearLayoutManager llm = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(llm);
        // mWebSocketClient.sendMessage(Helper.formSubscribeMessage("S", keyid, token));
        Person person = (Person) getIntent().getSerializableExtra("person");
        keyid = person.getPostId();
        Log.d(TAG, "keyid:" + keyid);
        if (keyid != null) {

            mComments.clear();

          //  mComments.addAll(mDBHelper.getCommentData(keyid));

            mComments.add(0, person);
            mCommentListAdapter = new FeedRVAdapter(DiscussionActivity.this,mComments);
            recyclerView.setAdapter(mCommentListAdapter);
            Log.d(TAG, "This will be used to enable Type message layout:" + person.getSubscriptionFlag());
            //PostSubscription(view,keyid, mComments.get(0).getUserId(),"Y");
           // Helper.PostSubscription(view,token, keyid, "Y");
        }


        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = mMessageBox.getText().toString();
                if (!TextUtils.isEmpty(msg)) {

                    Log.d(TAG, "Messaage:" + msg);
                    Log.d(TAG, "Token:" + token);
                    mWebSocketClient.connectToWSS(WS_URL + "/" + ((OurApplication) getApplicationContext()).getUserToken());
                    msg = Helper.formCommentMessage("C", keyid, token, msg);

                    Log.d(TAG, "Formcommentmessage" + msg);
                    mWebSocketClient.sendMessage(msg);
                    UI.closeKeyboard(getApplicationContext(), mMessageBox.getWindowToken());
                    mSwipeRefreshLayout.post(new Runnable() {
                        @Override
                        public void run() {
                            mSwipeRefreshLayout.setRefreshing(true);
                            getUpdatedComments();
                        }
                    });
                    mMessageBox.setText(null);

                }
            }
        });

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swiperefresh);
       /* mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {

             //This method is called when swipe refresh is pulled down

            @Override
            public void onRefresh() {
                // Refresh items
                getUpdatedComments();
            }

        });*/

        /*
         * Showing Swipe Refresh animation on activity create
         * As animation won't start on onCreate, post runnable is used
         */
        mSwipeRefreshLayout.post(new Runnable() {
            @Override
            public void run() {
                mSwipeRefreshLayout.setRefreshing(true);
                getUpdatedComments();
            }
        });
    }

    @Override
    public void onOpen() {}

    @Override
    public void onClose() {}

    @Override
    public void onTextMessage(final String message) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    ObjectMapper objectMapper = new ObjectMapper();
                    Person person = objectMapper.readValue(message, Person.class);

                    Log.d(TAG, message);

                    if (person.getType().equals("C")) {
                        Log.d(TAG, "I am message type C:");
                        Log.d(TAG, person.getPostId());

                        if (person.getPostId().equals(keyid)) {
                            //Add to Comment array if it belongs to same post id and notify dataset changed
                            mComments.add(person);
                            mCommentListAdapter.notifyDataSetChanged();
                        }

                        //Notify using Inbox style
                        //Notify(mDBHelper.getProfileInfo(msgObject.optString(Keys.KEY_NAME), 1),
                        //      msgObject.optString(Keys.KEY_MESSAGE),
                        //    mDBHelper.getProfileInfo(msgObject.optString(Keys.KEY_NAME), 2));
                    }

                } catch (JsonParseException e) {
                    e.printStackTrace();
                } catch (JsonMappingException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //mWebSocketClient.sendMessage(Helper.formSubscribeMessage("U", keyid, token));
        mWebSocketClient.removeWebSocketListener(this);
        //PostSubscription(view,keyid, mComments.get(0).getUserId(),"N");
       // Helper.PostSubscription(view, token,keyid, "N");
    }

    private void getUpdatedComments(){
        CommentFeedReqModel reqModel = new CommentFeedReqModel();
        //reqModel.setLatestDate("2016-12-11 17:00:00");
        reqModel.setLatestDate(mDBHelper.getCommentDataLatestTime(keyid));

        reqModel.setPostId(keyid);
        reqModel.setType("C");
        Call<SuccessRespModel> queryComments = ((OurApplication)getApplicationContext())
                .getRestApi().queryCommentFeed(reqModel);
        queryComments.enqueue(new Callback<SuccessRespModel>() {
            @Override
            public void onResponse(Call<SuccessRespModel> call, Response<SuccessRespModel> response) {

                ArrayList<Person> data = response.body().getData();
                if (data.size() > 0) {
                    for (int i = 0; i < data.size(); i++) {
                        //  mDBHelper.insertCommentData(data.get(i));
                        mComments.add(data.get(i));
                        //mComments.addAll(response.body().getData());
                        mCommentListAdapter.notifyDataSetChanged();

                    }

                    Toast.makeText(getApplicationContext(), "New Comments", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getApplicationContext(), "No more Comments to Load", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<SuccessRespModel> call, Throwable t) {
                Log.d(TAG, "Query failed");
                Toast.makeText(getApplicationContext(), "Loading Comments Failed", Toast.LENGTH_LONG).show();
            }
        });
        mSwipeRefreshLayout.setRefreshing(false);
    }


}