package gabiloo.ucool;

/**
 * Created by lucas e  gabriel on 20/05/17.
 */

import android.graphics.Bitmap;
import android.provider.MediaStore;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;


public class ImageCatch implements Raw.DataCallback{

    private static final int Width = 80;
    private static final int Height = 60;

    private int[] Area = new int[Height * Width];
    private ByteBuffer Area4 = ByteBuffer.allocateDirect(Height * Width * 4);

    public interface UpdateListener {
        void onImageUpdated(Bitmap bitmap);
    }

    private UpdateListener Listener;

    public ImageCatch(UpdateListener listener) {
        Listener = listener;
    }

    private int mMax = 0;
    private int mMin = 0xFFFF;
    private int mNextId = 0;
    public int AreaCount = 0;

    public void PixUpdate() {
        int diff = mMax - mMin;
        float scale = (float)(255)/(float)diff; //255 = 2⁸-1

        Area4.rewind();

        for (int idx = 0; idx < Area.length; idx++) {
            int offset = Area[idx] - mMin;
            int mapIdx = Math.round((float)offset * scale);
            Area4.put((byte)mapIdx);
            Area4.put((byte)0x00);
            Area4.put((byte)(0xFF - mapIdx));
            Area4.put((byte)0xFF);
        }

        Area4.clear();

        if (Listener != null) {
            Bitmap bm = Bitmap.createBitmap(Width, Height, Bitmap.Config.ARGB_8888);
            bm.copyPixelsFromBuffer(Area4);
            Listener.onImageUpdated(bm);

        }
    }

    @Override
    public void onData(byte[] data, int len) {
        ByteBuffer buffer = ByteBuffer.wrap(data, 0, len);
        buffer.order(ByteOrder.BIG_ENDIAN);

        boolean sof = false;
        int sofar = 0;
        while (sofar < len) {
            if (AreaCount % Width == 0) {
                sof = true;
            }

            if (sof) {
                int id = buffer.getShort();
                id &= 0x0FFF;
                int crc = buffer.getShort();
                sof = false;
                sofar += 4;
                if (id != mNextId) {
                    AreaCount = 0;
                    mNextId = 0;
                    break;
                } else {
                    mNextId++;
                }
            }
            int val = buffer.getShort();
            sofar += 2;

            val &= 0x3FFF;
            Area[AreaCount] = val;
            AreaCount++;

            if (val > mMax)
                mMax = val;

            if (val < mMin)
                mMin = val;
        }

        if (AreaCount == Area.length) {
            PixUpdate();
            mMax = 0;
            mMin = 0xFFFF;
            mNextId = 0;
            AreaCount = 0;
        }
    }

}
