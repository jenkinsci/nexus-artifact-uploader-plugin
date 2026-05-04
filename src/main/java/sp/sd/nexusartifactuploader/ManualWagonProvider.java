package sp.sd.nexusartifactuploader;

import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.providers.http.HttpWagon;
import org.sonatype.aether.connector.wagon.WagonProvider;

public class ManualWagonProvider implements WagonProvider {
    @Override
    public Wagon lookup(String roleHint) throws Exception {

        return new HttpWagon();
    }

    @Override
    public void release(Wagon arg0) {}
}
