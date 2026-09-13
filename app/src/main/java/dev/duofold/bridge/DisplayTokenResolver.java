package dev.duofold.bridge;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Resolves the default physical display across AOSP and vendor hidden-API layouts. */
final class DisplayTokenResolver {
    private DisplayTokenResolver() {}

    static Object resolve(Class<?>... providers) throws Exception {
        Throwable last=null;
        for(Class<?> provider:providers) {
            if(provider==null)continue;
            try {
                Method direct=provider.getMethod("getInternalDisplayToken");
                Object token=direct.invoke(null);
                if(token!=null)return token;
            } catch(NoSuchMethodException e) {last=e;}
            catch(InvocationTargetException e) {last=e.getCause()==null?e:e.getCause();}
            try {
                long[] ids=(long[])provider.getMethod("getPhysicalDisplayIds").invoke(null);
                if(ids!=null)for(long id:ids) {
                    Object token=provider.getMethod("getPhysicalDisplayToken",long.class).invoke(null,id);
                    if(token!=null)return token;
                }
            } catch(NoSuchMethodException e) {last=e;}
            catch(InvocationTargetException e) {last=e.getCause()==null?e:e.getCause();}
        }
        throw new IllegalStateException("No physical display token API is available",last);
    }
}
