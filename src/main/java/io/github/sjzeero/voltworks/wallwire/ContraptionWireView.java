package io.github.sjzeero.voltworks.wallwire;

import java.util.List;
import java.util.UUID;

public interface ContraptionWireView {
    List<ContraptionWires.Drawn> voltworks$drawn();

    UUID voltworks$frozenId();
}
