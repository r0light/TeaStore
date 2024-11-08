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

package tools.descartes.teastore.webui.servlet;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.ehcache.Cache;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import tools.descartes.teastore.registryclient.Service;
import tools.descartes.teastore.registryclient.loadbalancers.LoadBalancerTimeoutException;
import tools.descartes.teastore.registryclient.rest.LoadBalancedCRUDOperations;
import tools.descartes.teastore.registryclient.rest.LoadBalancedImageOperations;
import tools.descartes.teastore.registryclient.rest.LoadBalancedRecommenderOperations;
import tools.descartes.teastore.registryclient.rest.LoadBalancedStoreOperations;
import tools.descartes.teastore.entities.Category;
import tools.descartes.teastore.entities.ImageSizePreset;
import tools.descartes.teastore.entities.OrderItem;
import tools.descartes.teastore.entities.Product;
import tools.descartes.teastore.entities.message.SessionBlob;

/**
 * Servlet implementation for the web view of "Cart".
 * 
 * @author Andre Bauer
 */
@WebServlet("/cart")
public class CartServlet extends AbstractUIServlet {
  private static final long serialVersionUID = 1L;

  private Cache<String, List<Category>> categoriesCache;
  private Cache<String, String> webImageCache;
  private Cache<Integer, List<Long>> recommendationsCache;
  private Cache<Long, Product> productCache;
  private Cache<Long, String> productImageCache;

  /**
   * @see HttpServlet#HttpServlet()
   */
  public CartServlet() {
    super();
    this.categoriesCache = CachingHelper.getCacheManager().createCache("cartCategoriesCache",
            CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, (Class) List.class,
                            ResourcePoolsBuilder.heap(10))
                    .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofSeconds(10)))
                    .build()
    );
    this.webImageCache = CachingHelper.getCacheManager().createCache("webImageCache",
            CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, String.class,
                            ResourcePoolsBuilder.heap(10))
                    .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofSeconds(10)))
                    .build()
    );
    this.recommendationsCache = CachingHelper.getCacheManager().createCache("recommendationsCache",
            CacheConfigurationBuilder.newCacheConfigurationBuilder(Integer.class, (Class) List.class,
                            ResourcePoolsBuilder.heap(500))
                    .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofSeconds(100)))
                    .build()
    );
    this.productCache = CachingHelper.getCacheManager().createCache("productCache",
            CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, Product.class,
                            ResourcePoolsBuilder.heap(10))
                    .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofSeconds(500)))
                    .build()
    );
    this.productImageCache = CachingHelper.getCacheManager().createCache("productImageCache",
            CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, String.class,
                            ResourcePoolsBuilder.heap(500))
                    .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofSeconds(10)))
                    .build()
    );
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void handleGETRequest(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException, LoadBalancerTimeoutException {
    checkforCookie(request, response);
    SessionBlob blob = getSessionBlob(request);

    List<OrderItem> orderItems = blob.getOrderItems();
    ArrayList<Long> ids = new ArrayList<Long>();
    for (OrderItem orderItem : orderItems) {
      ids.add(orderItem.getProductId());
    }

    HashMap<Long, Product> products = new HashMap<Long, Product>();
    for (Long id : ids) {
      Product product = LoadBalancedCRUDOperations.getEntity(Service.PERSISTENCE, "products",
          Product.class, id);
      products.put(product.getId(), product);
    }

    if (webImageCache.containsKey("icon")) {
      request.setAttribute("storeIcon",webImageCache.get("icon"));
    } else {
      String storeIcon = LoadBalancedImageOperations.getWebImage("icon", ImageSizePreset.ICON.getSize());
      webImageCache.put("icon", storeIcon);
      request.setAttribute("storeIcon",storeIcon);
    }

    request.setAttribute("title", "TeaStore Cart");

    if (categoriesCache.containsKey("all")) {
      request.setAttribute("CategoryList", categoriesCache.get("all"));
    } else {
      List<Category> allCategories = LoadBalancedCRUDOperations
              .getEntities(Service.PERSISTENCE, "categories", Category.class, -1, -1);
      categoriesCache.put("all", allCategories);
      request.setAttribute("CategoryList", allCategories);
    }

    request.setAttribute("OrderItems", orderItems);
    request.setAttribute("Products", products);
    request.setAttribute("login", LoadBalancedStoreOperations.isLoggedIn(getSessionBlob(request)));


    List<Long> productIds = new LinkedList<>();
    Integer recommendationsHash = blob.getOrderItems()
            .stream()
            .map(OrderItem::getId)
            .map(Object::toString)
            .reduce("",((accumulator, itemId) -> accumulator + itemId))
            .concat(blob.getUID().toString())
            .hashCode();
    if (recommendationsCache.containsKey(recommendationsHash)) {
      productIds.addAll(recommendationsCache.get(recommendationsHash));
    } else  {
      List<Long> recommendedIds = LoadBalancedRecommenderOperations.getRecommendations(blob.getOrderItems(), blob.getUID());
      productIds.addAll(recommendedIds);
      recommendationsCache.put(recommendationsHash, recommendedIds);
    }

    List<Product> ads = new LinkedList<Product>();
    for (Long productId : productIds) {
      if (productCache.containsKey(productId)) {
        ads.add(productCache.get(productId));
      } else {
        Product adProduct = LoadBalancedCRUDOperations.getEntity(Service.PERSISTENCE, "products", Product.class,
                productId);
        productCache.put(productId, adProduct);
        ads.add(adProduct);
      }
    }

    if (ads.size() > 3) {
      ads.subList(3, ads.size()).clear();
    }
    request.setAttribute("Advertisment", ads);


    HashMap<Long, String> images = new HashMap<>();
    List<Product> needed = new LinkedList<>();
    ads.forEach(ad -> {
      if (productImageCache.containsKey(ad.getId())) {
        images.put(ad.getId(), productImageCache.get(ad.getId()));
      } else {
        needed.add(ad);
      }
    });

    if (needed.size() > 0) {
      HashMap<Long, String> fetched = LoadBalancedImageOperations.getProductImages(ads,
              ImageSizePreset.RECOMMENDATION.getSize());
      images.putAll(fetched);
      fetched.forEach((pid, image) -> {
        images.put(pid, image);
        productImageCache.put(pid, image);
      });
    }
    request.setAttribute("productImages", images);

    request.getRequestDispatcher("WEB-INF/pages/cart.jsp").forward(request, response);

  }

}
