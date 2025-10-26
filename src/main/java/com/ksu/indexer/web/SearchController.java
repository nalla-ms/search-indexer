
package com.ksu.indexer.web;

import com.ksu.indexer.service.SearchService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {
    private final SearchService searchService;
    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

  @GetMapping
  public List<String> search(@RequestParam String q) {
    return searchService.searchFileIdsLegacy(q); // change to strings
  }
    @GetMapping("/v2")
    public List<Map<String,Object>> searchV2(@RequestParam String q) {
        return searchService.searchV2(q);
    }
}
