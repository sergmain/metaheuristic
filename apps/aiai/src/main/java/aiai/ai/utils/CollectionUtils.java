package aiai.ai.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CollectionUtils {


    public static List<String> toPlainList(Collection<List<String>> inputResourceCodes) {
        final List<String> codes = new ArrayList<>();
        inputResourceCodes.forEach(codes::addAll);
        return codes;
    }

}
