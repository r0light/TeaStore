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
import tools.descartes.teastore.entities.Product;
import tools.descartes.teastore.registryclient.Service;
import tools.descartes.teastore.registryclient.loadbalancers.LoadBalancerTimeoutException;
import tools.descartes.teastore.registryclient.rest.LoadBalancedCRUDOperations;
import tools.descartes.teastore.registryclient.rest.LoadBalancedImageOperations;
import tools.descartes.teastore.registryclient.rest.LoadBalancedStoreOperations;
import tools.descartes.teastore.entities.Category;
import tools.descartes.teastore.entities.ImageSizePreset;

/**
 * Servlet implementation for the web view of "Order".
 * 
 * @author Andre Bauer
 */
@WebServlet("/order")
public class OrderServlet extends AbstractUIServlet {
	private static final long serialVersionUID = 1L;

	private Cache<String, List<Category>> categoriesCache;
	private Cache<String, String> webImageCache;

	/**
	 * @see HttpServlet#HttpServlet()
	 */
	public OrderServlet() {
		super();

		this.categoriesCache = CachingHelper.getCacheManager().createCache("orderCategoriesCache",
				CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, (Class) List.class,
								ResourcePoolsBuilder.heap(20))
						.withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofMinutes(2)))
						.build()
		);
		this.webImageCache = CachingHelper.getCacheManager().createCache("orderWebImageCache",
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
		if (getSessionBlob(request).getOrderItems().size() == 0) {
			redirect("/", response);
		} else {
			doPost(request, response);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void handlePOSTRequest(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException, LoadBalancerTimeoutException {

		if (categoriesCache.containsKey("all")) {
			request.setAttribute("CategoryList", categoriesCache.get("all"));
		} else {
			List<Category> allCategories = LoadBalancedCRUDOperations
					.getEntities(Service.PERSISTENCE, "categories", Category.class, -1, -1);
			categoriesCache.put("all", allCategories);
			request.setAttribute("CategoryList", allCategories);
		}

		if (webImageCache.containsKey("icon")) {
			request.setAttribute("storeIcon",webImageCache.get("icon"));
		} else {
			String storeIcon = LoadBalancedImageOperations.getWebImage("icon", ImageSizePreset.ICON.getSize());
			webImageCache.put("icon", storeIcon);
			request.setAttribute("storeIcon",storeIcon);
		}


		request.setAttribute("title", "TeaStore Order");
		request.setAttribute("login", LoadBalancedStoreOperations.isLoggedIn(getSessionBlob(request)));
		request.getRequestDispatcher("WEB-INF/pages/order.jsp").forward(request, response);
	}

}
