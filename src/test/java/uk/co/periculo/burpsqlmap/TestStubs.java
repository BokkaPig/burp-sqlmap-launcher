package uk.co.periculo.burpsqlmap;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.params.HttpParameterType;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.persistence.PersistedObject;
import burp.api.montoya.persistence.Persistence;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

/**
 * Montoya is an interface-only API, so the whole of it can be stubbed with
 * dynamic proxies. That is what lets the extension's logic be tested without a
 * running Burp.
 *
 * Anything NOT stubbable this way is a static factory on a Montoya interface
 * (for example HttpRequestResponse.httpRequestResponse), because those reach
 * into Burp's internal object factory and throw outside Burp. Keep that out of
 * testable code paths - see CandidateDetector.score(), which is pure for exactly
 * this reason.
 */
public final class TestStubs {

    private TestStubs() {
    }

    /** A proxy returning {@code values} by method name, and sane defaults otherwise. */
    @SuppressWarnings("unchecked")
    public static <T> T stub(Class<T> type, Map<String, Object> values) {
        return (T) Proxy.newProxyInstance(TestStubs.class.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (values.containsKey(name)) return values.get(name);
                    switch (name) {
                        case "toString": return "<stub " + type.getSimpleName() + ">";
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                        default: break;
                    }
                    Class<?> returnType = method.getReturnType();
                    if (returnType == boolean.class) return false;
                    if (returnType == int.class) return 0;
                    if (returnType == long.class) return 0L;
                    if (returnType == short.class) return (short) 0;
                    if (returnType.isInterface()) return stub(returnType, Map.of());
                    return null;
                });
    }

    public static ParsedHttpParameter param(String name, String value, HttpParameterType type) {
        return stub(ParsedHttpParameter.class, Map.of("name", name, "value", value, "type", type));
    }

    public static HttpRequest request(String method, String host, String path,
                                      ParsedHttpParameter... params) {
        HttpService service = stub(HttpService.class, Map.of("host", host, "secure", true));
        return stub(HttpRequest.class, Map.of(
                "method", method,
                "pathWithoutQuery", path,
                "url", "https://" + host + path,
                "parameters", List.of(params),
                "httpService", service));
    }

    /**
     * A MontoyaApi whose extension data is backed by the given map, so
     * persistence can be asserted on and a "restart" simulated by constructing a
     * second registry over the same map.
     */
    public static MontoyaApi apiWithStorage(Map<String, String> backing) {
        PersistedObject data = (PersistedObject) Proxy.newProxyInstance(
                TestStubs.class.getClassLoader(), new Class<?>[]{PersistedObject.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "setString" -> {
                        backing.put((String) args[0], (String) args[1]);
                        yield null;
                    }
                    case "getString" -> backing.get((String) args[0]);
                    default -> null;
                });
        Persistence persistence = stub(Persistence.class, Map.of("extensionData", data));
        return stub(MontoyaApi.class, Map.of("persistence", persistence));
    }
}
