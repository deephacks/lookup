package deephacks.lookup;

import org.junit.Test;

import java.util.Collection;

import static junit.framework.Assert.assertNotNull;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class TestLookup extends BaseTest {

  @Test
  public void test_lookup_interfaces() {
    compile(Provider1.class, Provider2.class);
    Service service = Lookup.get().lookup(Service.class);
    assertNotNull(service);
    Collection<Service> services = Lookup.get().lookupAll(Service.class);
    assertThat(services.size(), is(2));
  }

  @Test
  public void test_lookup_no_interface() {
    compile(ProviderNoInterface.class);
    ProviderNoInterface provider = Lookup.get().lookup(ProviderNoInterface.class);
    assertNotNull(provider);
  }
}
