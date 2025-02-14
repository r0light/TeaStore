/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tools.descartes.teastore.webui.servlet;

import java.io.IOException;
import java.time.Duration;
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
import tools.descartes.teastore.webui.servlet.elhelper.ELHelperUtils;
import tools.descartes.teastore.entities.Category;
import tools.descartes.teastore.entities.ImageSizePreset;
import tools.descartes.teastore.entities.OrderItem;
import tools.descartes.teastore.entities.Product;
import tools.descartes.teastore.entities.message.SessionBlob;

/**
 * Servlet implementation for the web view of "Product".
 *
 * @author Andre Bauer
 */
@WebServlet("/product")
public class ProductServlet extends AbstractUIServlet {
    private static final long serialVersionUID = 1L;

    private Cache<String, List<Category>> categoriesCache;
    private Cache<Long, Product> productCache;
    private Cache<Integer, List<Long>> recommendationsCache;
    private Cache<Long, String> productImageCache;
    private Cache<String, String> webImageCache;

    /**
     * @see HttpServlet#HttpServlet()
     */
    public ProductServlet() {
        super();
        this.categoriesCache = CachingHelper.getCacheManager().createCache("productCategoriesCache",
                CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, (Class) List.class,
                                ResourcePoolsBuilder.heap(20))
                        .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofMinutes(2)))
                        .build()
        );
        this.productCache = CachingHelper.getCacheManager().createCache("productCache",
                CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, Product.class,
                                ResourcePoolsBuilder.heap(200))
                        .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofMinutes(2)))
                        .build()
        );
        this.recommendationsCache = CachingHelper.getCacheManager().createCache("productRecommendationsCache",
                CacheConfigurationBuilder.newCacheConfigurationBuilder(Integer.class, (Class) List.class,
                                ResourcePoolsBuilder.heap(100))
                        .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofMinutes(2)))
                        .build()
        );
        this.productImageCache = CachingHelper.getCacheManager().createCache("productProductImageCache",
                CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, String.class,
                                ResourcePoolsBuilder.heap(200))
                        .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofMinutes(2)))
                        .build()
        );
        this.webImageCache = CachingHelper.getCacheManager().createCache("productWebImageCache",
                CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, String.class,
                                ResourcePoolsBuilder.heap(20))
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
            if (request.getParameter("id") != null) {
                Long id = Long.valueOf(request.getParameter("id"));

                if (categoriesCache.containsKey("all")) {
                    request.setAttribute("CategoryList", categoriesCache.get("all"));
                } else {
                    List<Category> allCategories = LoadBalancedCRUDOperations
                            .getEntities(Service.PERSISTENCE, "categories", Category.class, -1, -1);
                    categoriesCache.put("all", allCategories);
                    request.setAttribute("CategoryList", allCategories);
                }

                request.setAttribute("title", "TeaStore Product");
                SessionBlob blob = getSessionBlob(request);
                request.setAttribute("login", LoadBalancedStoreOperations.isLoggedIn(blob));

                Product p;
                if (productCache.containsKey(id)) {
                    p = productCache.get(id);
                    request.setAttribute("product", p);
                } else {
                    p = LoadBalancedCRUDOperations.getEntity(Service.PERSISTENCE, "products",
                            Product.class, id);
                    productCache.put(id, p);
                    request.setAttribute("product", p);
                }

                List<OrderItem> items = new LinkedList<>();
                OrderItem oi = new OrderItem();
                oi.setProductId(id);
                oi.setQuantity(1);
                items.add(oi);
                items.addAll(getSessionBlob(request).getOrderItems());

                Long userId = getSessionBlob(request).getUID() ==  null ? 0L : getSessionBlob(request).getUID();

                List<Long> productIds = new LinkedList<>();
                Integer recommendationsHash = items
                        .stream()
                        .filter(item -> item != null)
                        .map(OrderItem::getId)
                        .map(Object::toString)
                        .reduce("", ((accumulator, itemId) -> accumulator + itemId))
                        .concat(userId.toString())
                        .hashCode();
                if (recommendationsCache.containsKey(recommendationsHash)) {
                    productIds.addAll(recommendationsCache.get(recommendationsHash));
                } else {
                    List<Long> recommendedIds = LoadBalancedRecommenderOperations.getRecommendations(items,
                            getSessionBlob(request).getUID());
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

                if (productImageCache.containsKey(p.getId())) {
                    request.setAttribute("productImage", productImageCache.get(p.getId()));
                } else {
                    String image = LoadBalancedImageOperations.getProductImage(p);
                    productImageCache.put(p.getId(), image);
                    request.setAttribute("productImage", image);
                }

                if (webImageCache.containsKey("icon")) {
                    request.setAttribute("storeIcon", webImageCache.get("icon"));
                } else {
                    String storeIcon = LoadBalancedImageOperations.getWebImage("icon", ImageSizePreset.ICON.getSize());
                    webImageCache.put("icon", storeIcon);
                    request.setAttribute("storeIcon", storeIcon);
                }

                request.setAttribute("helper", ELHelperUtils.UTILS);

                request.getRequestDispatcher("WEB-INF/pages/product.jsp").forward(request, response);
            } else {
                redirect("/", response);
            }

    }

}
