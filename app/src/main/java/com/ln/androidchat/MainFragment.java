package com.ln.androidchat;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.MongoClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.socket.client.Socket;
import io.socket.emitter.Emitter;


/**
 * A chat fragment containing messages view and input form.
 */
public class MainFragment extends Fragment {

    public static final String DB_NAME="chatapp";
    public static final String HOST="10.0.3.2";
    public static final int PORT = 27017;
    public static final String MONGO_COLLECTION_ADDMESSAGE= "addMessage";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_MESSAGE = "data";


    private static final int REQUEST_LOGIN = 0;

    private static final int TYPING_TIMER_LENGTH = 600;
    private static final int REQUEST_CHOSE_FILE = 101;
    private static final String KEY_TIME = "time";

    private RecyclerView mMessagesView;
    private EditText mInputMessageView;
    private List<Message> mMessages = new ArrayList<Message>();
    private RecyclerView.Adapter mAdapter;
    private boolean mTyping = false;
    private Handler mTypingHandler = new Handler();
    private String mUsername;
    private Socket mSocket;
    private String date;

    private ImageButton btnAddFile;


    private Emitter.Listener onSaveMessage = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String userName = "", message = "",time = "";
                    JSONObject data = (JSONObject) args[0];
                    try {
                        userName = data.getString("username");
                        message = data.getString("message");
                        time = data.getString("timeMess");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    removeTyping(userName);
                    addMessage(userName, message,time, Message.TYPE_MESSAGE_OTHER);
                }
            });
        }
    };

    public MainFragment() {
        super();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mAdapter = new MessageAdapter(activity, mMessages);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

        ChatApplication app = (ChatApplication) getActivity().getApplication();
        mSocket = app.getSocket();
        mSocket.on(Socket.EVENT_CONNECT_ERROR, onConnectError);
        mSocket.on(Socket.EVENT_CONNECT_TIMEOUT, onConnectError);
        mSocket.on("sentDone", onSendFileSuccess);
        mSocket.on("user joined", onUserJoined);
//        mSocket.on("new message", onNewMessage);
        mSocket.on("save ok", onSaveMessage);
        mSocket.on("user left", onUserLeft);
        mSocket.on("typing", onTyping);
        mSocket.on("stop typing", onStopTyping);
        mSocket.connect();

        startSignIn();
    }

    private void initMessage() {
        new MyAsyncTask().execute();
    }

    private class MyAsyncTask extends AsyncTask<Void, Void, Void> {
        MongoClient mongoClient;
        List<String> dbName;
        DBCollection dbCollection;
        DBCursor cursor;
        List<Message> mMessageUpdate = new ArrayList<Message>();

        @Override
        protected Void doInBackground(Void... params) {
            dbName = mongoClient.getDatabaseNames();

            DB db = mongoClient.getDB(DB_NAME);

            dbCollection = db.getCollection(MONGO_COLLECTION_ADDMESSAGE);
            cursor = dbCollection.find();
            //Log.i("CUSO", cursor.next().toString());

            //Log.i("CUSO", cursor.next().toString());
//            Log.i("CUSO", cursor.curr().get("name").toString());
            while (cursor.hasNext()){
                cursor.next();
                Log.i("AAAAAAAAAA",cursor.next().toString());
                String username = cursor.curr().get(KEY_USERNAME).toString();
                String message = cursor.curr().get(KEY_MESSAGE).toString();
                String time;
                if(cursor.curr().get(KEY_TIME) == null){
                    time = "--------------------";
                }
                else {
                    time = cursor.curr().get(KEY_TIME).toString();
                }
                //String time = cursor.curr().get(KEY_TIME).toString();
                //String time = "111";
                Log.i("ALLLL",username);

                Message message1 = new Message(username,message,time);
                mMessageUpdate.add(message1);
                /*
                if (username.equals(mUsername)) {
                    addMessage(username, message,Message.TYPE_MESSAGE);
                }
                else {
                    addMessage(username,message,Message.TYPE_MESSAGE_OTHER);
                }*/
            }
            return null;
        }

        @Override
        protected void onPreExecute() {
            try{
                mongoClient = new MongoClient(HOST,PORT);
                Log.i("Aloha", "Chay ngon vl");
            }
            catch (Exception e) {
                Log.i("Aloha", "Chay khong ngon ji ca");
            }
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            //Log.i(TAG, mongoClient.getAddress().toString());
            //Log.i(TAG, dbName.size() + "");
            //for (String dbNameABC : dbName){
                //Log.i(TAG, "DB Name "+ dbNameABC);
            //}
            for(int i =0;i<mMessageUpdate.size();i++){
                if (mMessageUpdate.get(i).getUsername().equals(mUsername)) {
                    addMessage(mMessageUpdate.get(i).getUsername(), mMessageUpdate.get(i).getMessage(),mMessageUpdate.get(i).getmTime() ,Message.TYPE_MESSAGE);
                }
                else {
                    addMessage(mMessageUpdate.get(i).getUsername(), mMessageUpdate.get(i).getMessage(),mMessageUpdate.get(i).getmTime(),Message.TYPE_MESSAGE_OTHER);
                }
            }
        }
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mSocket.disconnect();
        mSocket.off(Socket.EVENT_CONNECT_ERROR, onConnectError);
        mSocket.off(Socket.EVENT_CONNECT_TIMEOUT, onConnectError);
//        mSocket.off("new message", onNewMessage);
        mSocket.off("save ok", onSaveMessage);
        mSocket.off("user joined", onUserJoined);
        mSocket.off("user left", onUserLeft);
        mSocket.off("typing", onTyping);
        mSocket.off("stop typing", onStopTyping);
        mSocket.off("sentDone", onSendFileSuccess);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mMessagesView = (RecyclerView) view.findViewById(R.id.messages);
        mMessagesView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mMessagesView.setAdapter(mAdapter);

        mInputMessageView = (EditText) view.findViewById(R.id.message_input);
        mInputMessageView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int id, KeyEvent event) {
                if (id == R.id.send || id == EditorInfo.IME_NULL) {
                    attemptSend();
                    return true;
                }
                return false;
            }
        });
        mInputMessageView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (null == mUsername) return;
                if (!mSocket.connected()) return;

                if (!mTyping) {
                    mTyping = true;
                    mSocket.emit("typing");
                }

                mTypingHandler.removeCallbacks(onTypingTimeout);
                mTypingHandler.postDelayed(onTypingTimeout, TYPING_TIMER_LENGTH);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        ImageButton sendButton = (ImageButton) view.findViewById(R.id.send_button);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptSend();
            }
        });

        btnAddFile = (ImageButton) view.findViewById(R.id.add_file_button);
        btnAddFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addFile();
            }
        });
    }

    private void addFile() {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_CHOSE_FILE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_LOGIN:
                if (Activity.RESULT_OK != resultCode) {
                    //getActivity().finish();
                    //Toast.makeText(getActivity(), "Nhap sai email hoac mat khau", Toast.LENGTH_SHORT).show();
                    return;
                }
                else {
                    mUsername = data.getStringExtra("username");
                    initMessage();
                }
                break;
            case REQUEST_CHOSE_FILE:
                if (resultCode == Activity.RESULT_OK) {
                    Uri uri = data.getData();
                    String fileName = getFileName(uri);
                    Toast.makeText(getContext(), fileName, Toast.LENGTH_SHORT).show();
                    try {
                        InputStream inputStream = getActivity().getContentResolver().openInputStream(uri);
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        Base64OutputStream output64 = new Base64OutputStream(output, Base64.DEFAULT);
                        try {
                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                output64.write(buffer, 0, bytesRead);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        output64.close();

                        String attachedFile = output.toString();
                        mSocket.emit("sendPhoto", attachedFile, fileName);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                break;
            default:
                break;
        }
    }
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.menu_main, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();


        if (id == R.id.action_leave) {
            leave();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void addLog(String message) {
        mMessages.add(new Message.Builder(Message.TYPE_LOG)
                .message(message).build());
        mAdapter.notifyItemInserted(mMessages.size() - 1);
        scrollToBottom();
    }

    private void addParticipantsLog(int numUsers) {
        addLog(getResources().getQuantityString(R.plurals.message_participants, numUsers, numUsers));
    }

    private void addMessage(String username, String message,String time, int type) {
        mMessages.add(new Message.Builder(type)
                .username(username).message(message).time(time).build());
        mAdapter.notifyItemInserted(mMessages.size() - 1);
        scrollToBottom();
    }

    private void addTyping(String username) {
        mMessages.add(new Message.Builder(Message.TYPE_ACTION)
                .username(username).build());
        mAdapter.notifyItemInserted(mMessages.size() - 1);
        scrollToBottom();
    }

    private void removeTyping(String username) {
        for (int i = mMessages.size() - 1; i >= 0; i--) {
            Message message = mMessages.get(i);
            if (message.getType() == Message.TYPE_ACTION && message.getUsername().equals(username)) {
                mMessages.remove(i);
                mAdapter.notifyItemRemoved(i);
            }
        }
    }

    private void attemptSend() {
        if (null == mUsername) return;
        if (!mSocket.connected()) return;

        mTyping = false;

        String message = mInputMessageView.getText().toString().trim();
        if (TextUtils.isEmpty(message)) {
            mInputMessageView.requestFocus();
            return;
        }
        String date = getDate();
        mInputMessageView.setText("");
        addMessage(mUsername, message,date,Message.TYPE_MESSAGE);

        mSocket.emit("new message", date, message);
    }

    private String getDate() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MMM/yyyy-hh:mm:ss");
        String date = dateFormat.format(new Date(System.currentTimeMillis()));
        //this.date = date;
        return date;
    }

    private void startSignIn() {
        mUsername = null;
        Intent intent = new Intent(getActivity(), LoginActivity.class);
        startActivityForResult(intent, REQUEST_LOGIN);
    }

    private void leave() {
        mUsername = null;
        mSocket.disconnect();
        mSocket.connect();
        startSignIn();
    }

    private void scrollToBottom() {
        mMessagesView.scrollToPosition(mAdapter.getItemCount() - 1);
    }

    private Emitter.Listener onConnectError = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getActivity().getApplicationContext(),
                            R.string.error_connect, Toast.LENGTH_LONG).show();
                }
            });
        }
    };

//    private Emitter.Listener onNewMessage = new Emitter.Listener() {
//        @Override
//        public void call(final Object... args) {
//            getActivity().runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
//                    JSONObject data = (JSONObject) args[0];
//                    String username;
//                    String message;
//                    try {
//                        username = data.getString("username");
//                        message = data.getString("message");
//                    } catch (JSONException e) {
//                        return;
//                    }
//
//                }
//            });
//        }
//    };

    private Emitter.Listener onUserJoined = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    int numUsers;
                    try {
                        username = data.getString("username");
                        numUsers = data.getInt("numUsers");
                    } catch (JSONException e) {
                        return;
                    }

                    addLog(getResources().getString(R.string.message_user_joined, username));
                    addParticipantsLog(numUsers);
                }
            });
        }
    };

    private Emitter.Listener onUserLeft = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    int numUsers;
                    try {
                        username = data.getString("username");
                        numUsers = data.getInt("numUsers");
                    } catch (JSONException e) {
                        return;
                    }

                    addLog(getResources().getString(R.string.message_user_left, username));
                    addParticipantsLog(numUsers);
                    removeTyping(username);
                }
            });
        }
    };

    private Emitter.Listener onTyping = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    try {
                        username = data.getString("username");
                    } catch (JSONException e) {
                        return;
                    }
                    addTyping(username);
                }
            });
        }
    };

    private Emitter.Listener onStopTyping = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    try {
                        username = data.getString("username");
                    } catch (JSONException e) {
                        return;
                    }
                    removeTyping(username);
                }
            });
        }
    };

    private Emitter.Listener onSendFileSuccess = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String fileName, path;
                    try {
                        fileName = data.getString("name");
                        path = data.getString("path");
                        Log.i("FILENAME", fileName);
                    } catch (JSONException e) {
                        return;
                    }
                    String date = getDate();
                    addMessage(mUsername, fileName,date, Message.TYPE_MESSAGE);
                }
            });
        }
    };

    private Runnable onTypingTimeout = new Runnable() {
        @Override
        public void run() {
            if (!mTyping) return;

            mTyping = false;
            mSocket.emit("stop typing");
        }
    };

    public String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContext().getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }
}

