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
import tools.descartes.teastore.registryclient.rest.LoadBalancedStoreOperations;
import tools.descartes.teastore.webui.servlet.elhelper.ELHelperUtils;
import tools.descartes.teastore.entities.Category;
import tools.descartes.teastore.entities.ImageSizePreset;
import tools.descartes.teastore.entities.Order;
import tools.descartes.teastore.entities.User;

/**
 * Servlet implementation for the web view of "Profile".
 * 
 * @author Andre Bauer
 */
@WebServlet("/profile")
public class ProfileServlet extends AbstractUIServlet {

  private static final long serialVersionUID = 1L;

  private Cache<String, List<Category>> categoriesCache;
  private Cache<String, String> webImageCache;
  private Cache<Long, User> userCache;
  private Cache<Long, List<Order>> ordersCache;

  /**
   * @see HttpServlet#HttpServlet()
   */
  public ProfileServlet() {
    super();
    this.categoriesCache = CachingHelper.getCacheManager().createCache("profileCategoriesCache",
            CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, (Class) List.class,
                            ResourcePoolsBuilder.heap(20))
                    .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofMinutes(2)))
                    .build()
    );
    this.webImageCache = CachingHelper.getCacheManager().createCache("profileWebImageCache",
            CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, String.class,
                            ResourcePoolsBuilder.heap(20))
                    .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofMinutes(2)))
                    .build()
    );
    this.userCache = CachingHelper.getCacheManager().createCache("profileUserCache",
            CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, User.class,
                            ResourcePoolsBuilder.heap(50))
                    .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofMinutes(2)))
                    .build()
    );
    this.ordersCache = CachingHelper.getCacheManager().createCache("profileOrdersCache",
            CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, (Class) List.class,
                            ResourcePoolsBuilder.heap(100))
                    .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofMinutes(2)))
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
    if (!LoadBalancedStoreOperations.isLoggedIn(getSessionBlob(request))) {
      redirect("/", response);
    } else {

      if (webImageCache.containsKey("icon")) {
        request.setAttribute("storeIcon",webImageCache.get("icon"));
      } else {
        String storeIcon = LoadBalancedImageOperations.getWebImage("icon", ImageSizePreset.ICON.getSize());
        webImageCache.put("icon", storeIcon);
        request.setAttribute("storeIcon",storeIcon);
      }

      if (categoriesCache.containsKey("all")) {
        request.setAttribute("CategoryList", categoriesCache.get("all"));
      } else {
        List<Category> allCategories = LoadBalancedCRUDOperations
                .getEntities(Service.PERSISTENCE, "categories", Category.class, -1, -1);
        categoriesCache.put("all", allCategories);
        request.setAttribute("CategoryList", allCategories);
      }

      request.setAttribute("title", "TeaStore Home");

      if (userCache.containsKey(getSessionBlob(request).getUID())) {
        request.setAttribute("User", userCache.get(getSessionBlob(request).getUID()));
      } else {
        User user = LoadBalancedCRUDOperations.getEntity(Service.PERSISTENCE,
                "users", User.class, getSessionBlob(request).getUID());
        userCache.put(getSessionBlob(request).getUID(), user);
        request.setAttribute("User", user);
      }

      if (ordersCache.containsKey(getSessionBlob(request).getUID())) {
        request.setAttribute("Orders", ordersCache.get(getSessionBlob(request).getUID()));
      } else {
        List<Order> orders = LoadBalancedCRUDOperations.getEntities(Service.PERSISTENCE,
                "orders", Order.class, "user", getSessionBlob(request).getUID(), -1, -1);
        ordersCache.put(getSessionBlob(request).getUID(), orders);
        request.setAttribute("Orders", orders);
      }

      request.setAttribute("login",
          LoadBalancedStoreOperations.isLoggedIn(getSessionBlob(request)));
      request.setAttribute("helper", ELHelperUtils.UTILS);

      request.getRequestDispatcher("WEB-INF/pages/profile.jsp").forward(request, response);
    }
  }

}
