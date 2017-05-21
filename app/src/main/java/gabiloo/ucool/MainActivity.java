package gabiloo.ucool;

/**
 * Created by lucas e gabriel on 20/05/17.
 */
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.motorola.mod.IModManager;
import com.motorola.mod.ModDevice;
import com.motorola.mod.ModInterfaceDelegation;
import com.motorola.mod.ModManager;
import com.motorola.mod.ModProtocol;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private Context mContext;
    private ModManager mMdManager;
    private ModDevice mModDevice;
    private ModInterfaceDelegation mMdDelegate;
    private Handler mHandler = new Handler();
    private Button mButton;
    private ImageView mImageView;
    private boolean mStarted = false;

    private Raw mRaw;

    private final int RequestPermission = 100;
    public enum PERMCODE {PermissionOk, PermissionChecking, PermissionNegate};

    private ServiceConnection mServiceConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className,
                                       IBinder binder) {
            IModManager svc = IModManager.Stub.asInterface(binder);
            mMdManager = new ModManager(mContext, svc);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    findModDevice();
                }
            });
            //finish(); nem inicia o app
        }

        public void onServiceDisconnected(ComponentName className) {
            mMdManager = null;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    clearModDevice();
                }
            });
        }
    };

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ModManager.ACTION_MOD_ATTACH.equals(action)) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        findModDevice();
                    }
                });
            } else if (ModManager.ACTION_MOD_DETACH.equals(action)) {
                clearModDevice();
            }
        }
    };

    public void startRawDevice() {
        mRaw = new Raw(mMdManager, mMdDelegate);

        if (mRaw != null) {
            mRaw.setCallback(mImageCatch);
            mRaw.startReading();
            mStarted = true;
            //finish(); fecha apos start
        }
    }


    private String saveToInternalStorage(Bitmap bitmapImage){
        ContextWrapper cw = new ContextWrapper(getApplicationContext());
        // path to /data/data/yourapp/app_data/imageDir
        File directory = cw.getDir(String.valueOf(Environment.getExternalStorageDirectory()), Context.MODE_PRIVATE);
        // Create imageDir
        File mypath=new File(directory,"profile.jpg");

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(mypath);
            // Use the compress method on the BitMap object to write image to the OutputStream
            bitmapImage.compress(Bitmap.CompressFormat.PNG, 100, fos);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return directory.getAbsolutePath();
    }


    private ImageCatch.UpdateListener mUpdateListener = new ImageCatch.UpdateListener() {
        @Override
        public void onImageUpdated(final Bitmap bitmap) {
            if (bitmap != null) {
                //saveToInternalStorage(bitmap);
                //finish(); fecha  quando sai a imagem

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mImageView != null) {
                            mImageView.setImageBitmap(bitmap);
                            //finish(); fecha quando tira foto
                        }
                    }
                });
            }
        }
    };

    private ImageCatch mImageCatch = new ImageCatch(mUpdateListener);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = this.getApplicationContext();

        mButton = (Button)this.findViewById(R.id.button);
        mImageView = (ImageView)this.findViewById(R.id.imageView);

        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mStarted) {
                    stopRawReading();
                    //finish(); da start e fecha na hora
                } else {
                    startRawReading();
                    //finish(); da start e fecha na hora
                }
                //startRawReading();
                //mStarted = true;
                updateUi();
            }
        });


        //Init. Bind Services
        Intent service = new Intent(ModManager.ACTION_BIND_MANAGER);
        service.setComponent(ModManager.MOD_SERVICE_NAME);
        mContext.bindService(service, mServiceConnection, Context.BIND_AUTO_CREATE);
        //Init. Attach
        IntentFilter filter = new IntentFilter(ModManager.ACTION_MOD_ATTACH);
        filter.addAction(ModManager.ACTION_MOD_DETACH);
        //Init. Permissions
        mContext.registerReceiver(mReceiver, filter, ModManager.PERMISSION_MOD_INTERNAL, null);
        updateUi();
    }


    private void updateUi() {
        if (mButton == null) {
            return;
        }

        if (mStarted == true) {
            mButton.setText("");
            //finish(); fecha assim que aperta start

        } else {
            mButton.setText("");
            //finish(); ele fecha assim que abre

        }
    }

    private void findModDevice() {
        if (mMdManager == null)
            return;
        try {
            List<ModDevice> l = mMdManager.getModList(false);
            if (l == null || l.size() == 0) {
                return;
            }

            for (ModDevice d : l) {
                if (d != null) {
                    mModDevice = d;
                    break;
                }
            }
        } catch (RemoteException e) {
        }
    }

    private void clearModDevice() {
        this.stopRawReading();
        mModDevice = null;
        this.updateUi();
        //finish(); n fecha
    }

    private void startRawReading() {
        PERMCODE code = checkRawProtocol();
        if (code == PERMCODE.PermissionOk) {
            startRawDevice();
            //finish(); fecha apos o start
        }
    }

    private void stopRawReading() {
        if (mRaw != null) {
            mRaw.stopReading();
            //finish(); fecha no stop
        }
        mStarted = false;
    }

    private PERMCODE checkRawProtocol() {
        try {
            List<ModInterfaceDelegation> devices;
            devices = mMdManager.getModInterfaceDelegationsByProtocol(mModDevice,
                    ModProtocol.Protocol.RAW);
            if (devices != null && !devices.isEmpty()) {
                mMdDelegate = devices.get(0);
                //finish(); aperta start e fecha

                if (mContext.checkSelfPermission(ModManager.PERMISSION_USE_RAW_PROTOCOL)
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(
                            new String[]{ModManager.PERMISSION_USE_RAW_PROTOCOL},
                            RequestPermission);
                    //finish(); nao fecha

                    return PERMCODE.PermissionChecking;
                } else {
                    //finish(); fecha assim que aperta start
                    return PERMCODE.PermissionOk;
                }
            }
        } catch (RemoteException e) {
        }
        return PERMCODE.PermissionNegate;
    }

}