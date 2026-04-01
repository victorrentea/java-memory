package victor.training.performance.leak;

import jakarta.annotation.PostConstruct;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import victor.training.performance.util.PerformanceUtil;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static victor.training.performance.util.PerformanceUtil.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("leak21")
public class Leak21_FragmentedNative { //WIP

  int nextSize = MB(1);
  List<ByteBuffer> activeBuffers = new ArrayList<>();

  @GetMapping
  public String leak(
      @RequestParam(required = false, defaultValue = "true") boolean frag) {
    List<ByteBuffer> newBuffers = new ArrayList<>();
    for (int i = 0; i < 100 / (frag?1:2); i++) { // 50 buffers remain
      newBuffers.add(ByteBuffer.allocateDirect(nextSize));
    }
    for (int i = 0; i < newBuffers.size(); i+= (frag?2:1)) {
      // frag=false => all buffers remain active: XXXXXXX
      // frag=true => one every two of them are freed: X-X-X-X-X-
      activeBuffers.add(newBuffers.get(i));
    }
    nextSize +=1000; // next buffers = bigger => won't fit in the spaces freed up
    int bufferTotalSize = activeBuffers.stream().mapToInt(ByteBuffer::capacity).sum();
    return "Fragmenting: "+frag + (frag?" ✅ (bad)":" 🚫 (good)")
           + " -> You can run it <a href='/leak21'>with✅</a> or <a href='/leak21?frag=false'>without🚫</a>" +
           "<br>Total capacity of "+ activeBuffers.size() + " buffers = " + humanSize(bufferTotalSize) +
           "<br>Process RAM = " + humanSize(osRssBytes()) +
           "<br> diff = " + humanSize(osRssBytes()-bufferTotalSize)
           ;
  }
}
// Credits: a kind soul at Devoxx BE 2025

/*
 NATIVE MEMORY FRAGMENTATION (off-heap, via malloc)
 ===================================================

    ██████  ██████  ██████  ██████  ██████ <- 1) allocation

    ██████  ░░░░░░  ██████  ░░░░░░  ██████ <- 2) free up chess pattern
     used    free    used    free

    ███████  ░░░░░░░  ███████ 3+4) next allocation + free

    ████████  ░░░░░░░░  ████████ 5+6) next allocation + free, etc:

 ❌ Full memory layout after 3 rounds (one row):

    ██░░██░░██░░██░░████░░████░░██████████░░██████████░░██████████░░
    ^^    ^^    ^^    ^^      ^^      ^^           ^^           ^^
    r1 r1 r1 r1  r2    r2      r3          r3          r3
          ↑ wasted gaps between all of them, never reused ↑

    RSS keeps growing! Live data ≈ 50% of RSS. The rest = wasted gaps.

 4) WITHOUT fragmentation (frag=false): all buffers contiguous, no gaps:

    ████████████████████████████████████████████████████░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░
    ^^^^^^^^^^^^^^^^^ all live buffers ^^^^^^^^^^^^^^^^  free space at end → reusable!
    RSS ≈ live data. No waste.

 Invisible to heap dumps & GC (off-heap!). Detect via: RSS vs heap, NMT, pmap.
 Fix: pool buffers (Netty), use jemalloc/tcmalloc, set -XX:MaxDirectMemorySize.
*/
