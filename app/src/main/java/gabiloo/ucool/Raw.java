package gabiloo.ucool;

/**
 * Created by lucas e gabriel on 20/05/17.
 */

import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;

import com.motorola.mod.ModInterfaceDelegation;
import com.motorola.mod.ModManager;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class Raw extends Thread {
    private ModManager mMdManager;
    private ModInterfaceDelegation mMdDelegate;
    private ParcelFileDescriptor mPFileDescriptor;
    private FileDescriptor[] mFileDescriptor;
    private OutputStream mOutStream;


    public Raw (ModManager ID, ModInterfaceDelegation Delegation) {
        this.mMdManager = ID;
        this.mMdDelegate = Delegation;
    }


    public interface DataCallback {
        void onData(byte[] data, int len);
    }

    private DataCallback mCallback;

    public void setCallback(DataCallback callback) {
        mCallback = callback;
    }

    @Override
    public void run() {
        if (!openDevice())
            return;

        mOutStream = new FileOutputStream(mPFileDescriptor.getFileDescriptor());

        sendOnCommand();

        blockRead();

        try {
            mOutStream.close();
        } catch (IOException e) {
        }

        closeDevice();
    }

    private boolean openDevice() {
        try {
            mPFileDescriptor = mMdManager.openModInterface(mMdDelegate,
                    ParcelFileDescriptor.MODE_READ_WRITE);
        } catch (RemoteException e) {
        }
        return true;
    }

    private void blockRead() {
        byte[] buffer = new byte[1024];
        FileDescriptor fd = mPFileDescriptor.getFileDescriptor();
        FileInputStream inputStream = new FileInputStream(fd);
        int ret = 0;
        synchronized (mFileDescriptor) {
            while (ret >= 0) {
                try {
                    if (readDevice()) {
                        ret = inputStream.read(buffer, 0, 1024);
                        if (ret > 0) {
                            if (mCallback != null) {
                                mCallback.onData(buffer, ret);
                            }
                        }
                    } else {
                        sendOffCommand();
                        break;
                    }
                } catch (IOException e) {
                    break;
                }
            }
        }
    }

    private boolean readDevice() {
        StructPollfd rawFd = new StructPollfd();
        rawFd.fd = mPFileDescriptor.getFileDescriptor();
        rawFd.events = (short) (OsConstants.POLLIN | OsConstants.POLLHUP);

        StructPollfd syncFd = new StructPollfd();
        syncFd.fd = mFileDescriptor[0];
        syncFd.events = (short) OsConstants.POLLIN;

        StructPollfd[] pollfds = new StructPollfd[2];
        pollfds[0] = rawFd;
        pollfds[1] = syncFd;

        try {
            int ret = Os.poll(pollfds, -1);
            if (ret > 0) {
                if (syncFd.revents == OsConstants.POLLIN) {
                    /** POLLIN on the syncFd as signal to exit */
                    byte[] buffer = new byte[1];
                    new FileInputStream(mFileDescriptor[0]).read(buffer, 0, 1);
                } else if ((rawFd.revents & OsConstants.POLLHUP) != 0) {
                    return false;
                } else if ((rawFd.revents & OsConstants.POLLIN) != 0) {
                    return true;
                } else {
                }
            } else {
            }
        } catch (ErrnoException e) {
        } catch (IOException e) {
        }
        return false;
    }


    private void closeDevice() {
        /** Close the file descriptor pipes */

        if (mPFileDescriptor != null) try {
            mPFileDescriptor.close();
            mPFileDescriptor = null;
        } catch (IOException e) {
        }
    }


    public synchronized void startReading() {
        try {
            mFileDescriptor = Os.pipe();
        } catch (ErrnoException e) {
            return;
        }
        start(); //Extends Thread foi utilizado para chamar o método start();
    }

    public synchronized void stopReading() {
        if (mFileDescriptor == null)
            return;

        FileOutputStream outstream = new FileOutputStream(mFileDescriptor[1]);
        try {
            outstream.write(0);
            outstream.close();
        } catch (IOException e) {
        }
        /*
        try {
            join();
        } catch (InterruptedException e) {
        }
        */
        try {
            Os.close(mFileDescriptor[0]);
            Os.close(mFileDescriptor[1]);
        } catch (ErrnoException e) {
        }
        mFileDescriptor = null;
    }

    private void sendOnCommand() {
        try {
            if (mOutStream != null) {
                byte[] mine = "on\0".getBytes();
                mOutStream.write(mine);
            }
        } catch (IOException e) {
        }
    }

    private void sendOffCommand() {
        try {
            if (mOutStream != null) {
                byte[] mine = "off\0".getBytes();
                mOutStream.write(mine);
            }
        } catch (IOException e) {
        }
    }





}
