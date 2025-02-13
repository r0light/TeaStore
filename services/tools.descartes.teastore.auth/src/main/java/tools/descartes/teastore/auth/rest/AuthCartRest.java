/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tools.descartes.teastore.auth.rest;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import org.ehcache.Cache;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import tools.descartes.teastore.auth.security.ShaSecurityProvider;
import tools.descartes.teastore.entities.OrderItem;
import tools.descartes.teastore.entities.Product;
import tools.descartes.teastore.entities.message.SessionBlob;
import tools.descartes.teastore.registryclient.Service;
import tools.descartes.teastore.registryclient.rest.LoadBalancedCRUDOperations;
import tools.descartes.teastore.registryclient.util.AvailabilityTimer;
import tools.descartes.teastore.registryclient.util.NotFoundException;
import tools.descartes.teastore.registryclient.util.TimeoutException;

import java.time.Duration;
import java.util.List;

/**
 * Rest endpoint for the store cart.
 * 
 * @author Simon
 */
@Path("cart")
@Produces({ "application/json" })
@Consumes({ "application/json" })
public class AuthCartRest {

  private AvailabilityTimer availabilityTimer = new AvailabilityTimer(2);

  private Cache<Long, Product> productCache;

  public AuthCartRest() {
    this.productCache = CachingHelper.getCacheManager().createCache("productCache",
            CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, Product.class,
                            ResourcePoolsBuilder.heap(100))
                    .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofSeconds(10)))
                    .build()
    );
  }

  /**
   * Adds product to cart. If the product is already in the cart the quantity is
   * increased.
   * 
   * @param blob
   *          Sessionblob
   * @param pid
   *          productid
   * @return Response containing session blob with updated cart
   */
  @POST
  @Path("add/{pid}")
  public Response addProductToCart(SessionBlob blob, @PathParam("pid") final Long pid) {

    if (availabilityTimer.isDown()) {
      return Response.serverError().build();
    }

    Product product;

      if (productCache.containsKey(pid)) {
        product = productCache.get(pid);
      } else {
        try {
          product = LoadBalancedCRUDOperations.getEntity(Service.PERSISTENCE, "products", Product.class,
                  pid);
          productCache.put(pid, product);
        } catch (TimeoutException e) {
          return Response.status(408).build();
        } catch (NotFoundException e) {
          return Response.status(404).build();
        }
      }

    for (OrderItem orderItem : blob.getOrderItems()) {
      if (orderItem.getProductId() == pid) {
        orderItem.setQuantity(orderItem.getQuantity() + 1);
        blob = new ShaSecurityProvider().secure(blob);
        return Response.status(Response.Status.OK).entity(blob).build();
      }
    }
    OrderItem item = new OrderItem();
    item.setProductId(pid);
    item.setQuantity(1);
    item.setUnitPriceInCents(product.getListPriceInCents());
    blob.getOrderItems().add(item);
    blob = new ShaSecurityProvider().secure(blob);
    return Response.status(Response.Status.OK).entity(blob).build();
  }

  /**
   * Remove product from cart.
   * 
   * @param blob
   *          Sessionblob
   * @param pid
   *          product id
   * @return Response containing Sessionblob with updated cart
   */
  @POST
  @Path("remove/{pid}")
  public Response removeProductFromCart(SessionBlob blob, @PathParam("pid") final Long pid) {

    if (availabilityTimer.isDown()) {
      return Response.serverError().build();
    }

    OrderItem toRemove = null;
    for (OrderItem item : blob.getOrderItems()) {
      if (item.getProductId() == pid) {
        toRemove = item;
      }
    }
    if (toRemove != null) {
      blob.getOrderItems().remove(toRemove);
      blob = new ShaSecurityProvider().secure(blob);
      return Response.status(Response.Status.OK).entity(blob).build();
    } else {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
  }

  /**
   * Updates quantity of product in cart.
   * 
   * @param blob
   *          Sessionblob
   * @param pid
   *          Productid
   * @param quantity
   *          New quantity
   * @return Response containing Sessionblob with updated cart
   */
  @PUT
  @Path("{pid}")
  public Response updateQuantity(SessionBlob blob, @PathParam("pid") final Long pid,
      @QueryParam("quantity") int quantity) {

    if (availabilityTimer.isDown()) {
      return Response.serverError().build();
    }

    for (OrderItem item : blob.getOrderItems()) {
      if (item.getProductId() == pid) {
        item.setQuantity(quantity);
        blob = new ShaSecurityProvider().secure(blob);
        return Response.status(Response.Status.OK).entity(blob).build();
      }
    }
    return Response.status(Response.Status.NOT_FOUND).build();
  }

}
