import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class TxProxy implements InvocationHandler {
    private final Object target;   // object thật nằm phía sau proxy

    private TxProxy(Object target) { this.target = target; }

    // Mọi lời gọi method trên proxy đều chạy qua đây
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Method real = target.getClass().getMethod(method.getName(), method.getParameterTypes());
        boolean hasTx = real.isAnnotationPresent(Tx.class);   // reflection: đọc annotation

        if (hasTx) System.out.println("BEGIN transaction  (" + method.getName() + ")");
        Object result = method.invoke(target, args);            // reflection: gọi method thật
        if (hasTx) System.out.println("COMMIT transaction (" + method.getName() + ")");
        return result;
    }

    @SuppressWarnings("unchecked")
    public static <T> T wrap(T target, Class<T> iface) {
        return (T) Proxy.newProxyInstance(
                iface.getClassLoader(), new Class<?>[]{ iface }, new TxProxy(target));
    }
}
