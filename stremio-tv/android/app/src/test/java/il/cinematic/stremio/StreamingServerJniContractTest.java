package il.cinematic.stremio;

import static org.junit.Assert.assertArrayEquals;

import com.stremio.mobile.server.JniStreamingServerController;

import android.content.Context;

import org.junit.Test;

import java.lang.reflect.Method;

public class StreamingServerJniContractTest {
    @Test
    public void nativeStartSignatureMatchesRustExport() throws Exception {
        Method method = JniStreamingServerController.class.getDeclaredMethod(
            "startServerNative",
            Context.class,
            String.class,
            String.class,
            int.class
        );

        assertArrayEquals(
            new Class<?>[] { Context.class, String.class, String.class, int.class },
            method.getParameterTypes()
        );
    }
}
