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

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.ehcache.Cache;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import tools.descartes.teastore.registryclient.loadbalancers.LoadBalancerTimeoutException;
import tools.descartes.teastore.registryclient.rest.LoadBalancedImageOperations;
import tools.descartes.teastore.entities.ImageSizePreset;

/**
 * Servlet implementation for the web view of "Database".
 * 
 * @author Andre Bauer
 */
@WebServlet("/database")
public class DataBaseServlet extends AbstractUIServlet {
	private static final long serialVersionUID = 1L;

	private Cache<String, String> webImageCache;

	/**
	 * @see HttpServlet#HttpServlet()
	 */
	public DataBaseServlet() {
		super();

		this.webImageCache = CachingHelper.getCacheManager().createCache("databaseWebImageCache",
				CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, String.class,
								ResourcePoolsBuilder.heap(10))
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

		if (webImageCache.containsKey("icon")) {
			request.setAttribute("storeIcon",webImageCache.get("icon"));
		} else {
			String storeIcon = LoadBalancedImageOperations.getWebImage("icon", ImageSizePreset.ICON.getSize());
			webImageCache.put("icon", storeIcon);
			request.setAttribute("storeIcon",storeIcon);
		}

		request.setAttribute("title", "TeaStore Database");
		request.getRequestDispatcher("WEB-INF/pages/database.jsp").forward(request, response);
	}


}
